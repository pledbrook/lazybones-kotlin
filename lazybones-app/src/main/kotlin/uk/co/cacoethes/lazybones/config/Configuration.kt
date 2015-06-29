package uk.co.cacoethes.lazybones.config

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.ConfigObject
import groovy.util.ConfigSlurper
import groovy.util.logging.Log
import java.io.*
import java.net.URI
import java.util.*

import java.util.logging.Level
import java.util.logging.Logger

/**
 * <p>Central configuration for Lazybones, although only the static data and methods
 * are Lazybones specific. The instance-level API works on the basis of settings
 * whose names are dot-separated and supplied from various sources: a base
 * configuration (typically hard-coded in the app), an app-managed JSON file that
 * can be updated at runtime, and a user-defined configuration.</p>
 * <p>The user-defined configuration takes precedence over the app-managed one,
 * which in turn takes precedence over the base config. When settings are changed
 * at runtime, the class attempts to warn the caller if any setting change will be
 * overridden by an existing user-defined value.</p>
 * <p>This class also maintains a list of valid settings: if it comes across any
 * settings that aren't in the list, it will throw an exception. It will also throw
 * an exception if the value of a setting isn't of the registered type. This ensures
 * that a user can get quick feedback about typos and other errors in their config.
 * </p>
 */
class Configuration(
        baseSettings : ConfigObject,
        val overrideSettings : Map<String, Any>,
        managedSettings : Map<String, Any>,
        val validOptions : Map<String, Class<*>>,
        val jsonConfigFile : File) {

    private val settings : ConfigObject
    private val managedSettings : ConfigObject

    init {
        this.settings = baseSettings
        addConfigEntries(managedSettings, this.settings)
        addConfigEntries(overrideSettings, this.settings)

        this.managedSettings = ConfigObject()
        addConfigEntries(managedSettings, this.managedSettings)

        processSystemProperties(this.settings)

        // Validate the provided settings to ensure that they are known and that
        // they have a value of the appropriate type.
        val invalidOptions = (this.settings.flatten() as Map<String, Any>).filter { entry ->
            !validateSetting(entry.key, validOptions, entry.value)
        }.keySet()

        if (invalidOptions.isNotEmpty()) {
            throw MultipleInvalidSettingsException(invalidOptions.toList())
        }
    }

    /**
     * Persists the managed config settings as JSON to the file located by
     * {@link #jsonConfigFile}.
     * @return A list of the keys in the managed config settings that are
     * overridden by values in the user config and system properties
     * (represented by a map of override settings).
     */
    public fun storeSettings() : List<String> {
        val sharedKeys = findIntersectKeys(managedSettings as Map<String, Any>, overrideSettings)
        jsonConfigFile.writeText(JsonBuilder(managedSettings).toPrettyString(), ENCODING)

        return sharedKeys
    }

    /**
     * Retrieves the value of a setting by name.
     * @param name The name of the setting as a dot-separated string.
     * @return The current value of the requested setting.
     * @throws UnknownSettingException If the setting name is not recognised,
     * i.e. it isn't in the registered list of known settings.
     */
    public fun getSetting(name : String) : Any? {
        requireSettingType(name)
        return getConfigOption(this.settings as Map<String, Any>, name)
    }

    /**
     * Retrieves a parent key from the current settings. This method won't work
     * for complete setting names (as defined in the known settings/valid
     * options map). For example, if the configuration has multiple 'repo.url.*'
     * entries, you can get all of them in one go by passing 'repo.url' to this
     * method.
     * @param rootSettingName The partial setting name (dot-separated) that you
     * want.
     * @return A map of the keys under the given setting name. The map will be empty
     * if there are no settings under the given key.
     * @throws UnknownSettingException If the partial setting name doesn't match
     * any of the settings in the known settings map.
     * @throws InvalidSettingException If the setting name is not partial but
     * is a complete match for an entry in the known settings map.
     */
    public fun getSubSettings(rootSettingName : String) : Map<String, Any> {
        if (validOptions.containsKey(rootSettingName)) {
            throw InvalidSettingException(rootSettingName, null, "'$rootSettingName' has no sub-settings")
        }

        val foundMatching = validOptions.any { entry ->
            entry.key.startsWith(rootSettingName + NAME_SEPARATOR) ||
                settingNameAsRegex(entry.key).toRegex().matches(rootSettingName)
        }
        if (!foundMatching) throw UnknownSettingException(rootSettingName)

        val setting = getConfigOption(this.settings as Map<String, Any>, rootSettingName)
        return (setting ?: hashMapOf<String, Any>()) as Map<String, Any>
    }

    /**
     * Returns all the current settings as a flat map. In other words, the keys
     * are the dot-separated names of the settings and there are no nested maps.
     * It's similar to converting the hierarchical ConfigObject into a Properties
     * object.
     */
    public fun getAllSettings() : Map<String, Any> {
        return settings.flatten() as Map<String, Any>
    }

    /**
     * Adds a new setting to the current configuration or updates the value of
     * an existing setting.
     * @param name The name of the setting to add/update (in dot-separated form).
     * @param value The new value for the setting. This value may be a
     * {@code CharSequence}, in which case this method attempts to convert it to
     * the appropriate type for the specified setting.
     * @return {@code true} if the new value is <b>not</b> overridden by the
     * existing user-defined configuration. If {@code false}, the new value will
     * take effect immediately, but won't survive the recreation of the {@link
     * Configuration} class.
     * @throws UnknownSettingException If the setting name doesn't match any of
     * those in the known settings map.
     * @throws InvalidSettingException If the given value is not of the appropriate
     * type for this setting, or if it cannot be converted to the correct type from
     * a string.
     */
    public fun putSetting(name : String, value : Any) : Boolean {
        val settingType = requireSettingType(name)

        val convertedValue : Any
        try {
            convertedValue =
                    if (value is CharSequence) Converters.requireConverter(settingType).toType(value.toString())
                    else requireValueOfType(name, value, settingType)
        }
        catch (all : Throwable) {
            log.log(Level.FINEST, all.getMessage(), all)
            throw InvalidSettingException(name, value)
        }

        setConfigOption(settings, name, convertedValue)
        setConfigOption(managedSettings, name, convertedValue)
        return getConfigOption(overrideSettings, name) == null
    }

    /**
     * Adds an extra value to an array/list setting.
     * @param name The dot-separated setting name to modify.
     * @param value The new value to add. If this is a {@code CharSequence}
     * then it is converted to the appropriate type.
     * @return {@code true} if the new value is <b>not</b> overridden by the
     * existing user-defined configuration. If {@code false}, the new value will
     * take effect immediately, but won't survive the recreation of the {@link
     * Configuration} class.
     * @throws UnknownSettingException If the setting name doesn't match any of
     * those in the known settings map.
     * @throws InvalidSettingException If the given value is not of the appropriate
     * type for this setting, or if it cannot be converted to the correct type from
     * a string.
     */
    public fun appendToSetting(name : String, value : Any) : Boolean {
        val settingType = requireSettingType(name)
        if (!settingType.isArray()) {
            throw InvalidSettingException(
                    name, value,
                    "Setting '${name}' is not an array type, so you cannot add to it")
        }

        val convertedValue = if (value is CharSequence) {
            Converters.requireConverter(settingType.getComponentType())?.toType(value.toString())
        }
        else {
            requireValueOfType(name, value, settingType.getComponentType())
        }

        getConfigOptionAsList(settings, name).add(convertedValue)
        getConfigOptionAsList(managedSettings, name).add(convertedValue)
        return getConfigOption(overrideSettings, name) == null
    }

    /**
     * Removes all values from an array/list setting.
     * @param name The dot-separated name of the setting to clear.
     * @throws UnknownSettingException If the setting name doesn't match any of
     * those in the known settings map.
     */
    public fun clearSetting(name : String) {
        requireSettingType(name)

        clearConfigOption(settings, name)
        clearConfigOption(managedSettings, name)
    }

    /**
     * Takes any config settings under the key "systemProp" and converts them
     * into system properties. For example, a "systemProp.http.proxyHost" setting
     * becomes an "http.proxyHost" system property in the current JVM.
     * @param config The configuration to load system properties from.
     */
    fun processSystemProperties(config : ConfigObject) {
        (config.getProperty("systemProp") as ConfigObject).flatten().forEach { entry ->
            System.setProperty(entry.key as String, entry.value?.toString())
        }
    }

    fun requireSettingType(name : String) : Class<*> {
        val settingType = getSettingType(name, validOptions)
        if (settingType == null) {
            throw UnknownSettingException(name)
        }
        return settingType
    }

    fun requireValueOfType(name : String, value : Any, settingType : Class<*>) : Any {
        if (valueOfType(value, settingType)) {
            return value
        }
        else {
            throw InvalidSettingException(name, value)
        }
    }

    fun valueOfType(value : Any, settingType : Class <*>) : Boolean {
        if (settingType.isArray() && value is List<*>) {
            return value.all { settingType.getComponentType().isAssignableFrom(it!!.javaClass) }
        }
        else {
            return settingType.isAssignableFrom(value.javaClass)
        }
    }
}

val SYSPROP_OVERRIDE_PREFIX = "lazybones."
val ENCODING = "UTF-8"
val JSON_CONFIG_FILENAME = "managed-config.json"
val NAME_SEPARATOR = "."
val NAME_SEPARATOR_REGEX = "\\."
val CONFIG_FILE_SYSPROP = "lazybones.config.file"

val VALID_OPTIONS = hashMapOf(
        "config.file" to javaClass<String>(),
        "cache.dir" to javaClass<String>(),
        "git.name" to javaClass<String>(),
        "git.email" to javaClass<String>(),
        "options.logLevel" to javaClass<String>(),
        "options.verbose" to javaClass<Boolean>(),
        "options.quiet" to javaClass<Boolean>(),
        "options.info" to javaClass<Boolean>(),
        "bintrayRepositories" to javaClass<Array<String>>(),
        "templates.mappings.*" to javaClass<URI>(),
        "systemProp.*" to javaClass<Any>()) +
        if (System.getProperty(CONFIG_FILE_SYSPROP)?.endsWith("test-config.groovy") ?: false) {
            hashMapOf(
                    "test.my.option" to javaClass<Int>(),
                    "test.option.override" to javaClass<String>(),
                    "test.option.array" to javaClass<Array<String>>(),
                    "test.integer.array" to javaClass<Array<Int>>(),
                    "test.other.array" to javaClass<Array<String>>(),
                    "test.adding.array" to javaClass<Array<String>>())
        }
        else hashMapOf()

val log = Logger.getLogger(javaClass<Configuration>().getName())

/**
 * <ol>
 *   <li>Loads the default configuration file from the classpath</li>
 *   <li>Works out the location of the user config file (either the default
 * or from a system property)</li>
 *   <li>Loads the user config file and merges with the default</li>
 *   <li>Overrides any config options with values provided as system properties</li>
 * </ol>
 * <p>The system properties take the form of 'lazybones.&lt;config.option&gt;'.</p>
 */
public fun initConfiguration() : Configuration {
    val defaultConfig = loadDefaultConfig()
    val userConfigFile = File(System.getProperty(CONFIG_FILE_SYSPROP) ?:
            defaultConfig.flatten()["config.file"] as String)
    val jsonConfigFile = getJsonConfigFile(userConfigFile)

    return initConfiguration(
            defaultConfig,
            if (userConfigFile.exists()) BufferedReader(InputStreamReader(userConfigFile.inputStream(), ENCODING))
            else StringReader(""),
            jsonConfigFile)
}

public fun initConfiguration(baseConfig : ConfigObject, userConfigSource : Reader, jsonConfigFile : File) : Configuration {
    val jsonConfig = HashMap<String, Any>()
    if (jsonConfigFile?.exists()) {
        jsonConfig.putAll(loadJsonConfig(BufferedReader(InputStreamReader(jsonConfigFile.inputStream(), ENCODING))))
    }

    val overrideConfig = loadConfig(userConfigSource)

    // Load settings from system properties. These override all other sources.
    loadConfigFromSystemProperties(overrideConfig)

    return Configuration(baseConfig, overrideConfig as Map<String, Any>, jsonConfig, VALID_OPTIONS, jsonConfigFile)
}

/**
 * Parses Groovy ConfigSlurper content and returns the corresponding
 * configuration as a ConfigObject.
 */
fun loadConfig(r : Reader) : ConfigObject {
    return ConfigSlurper().parse(r.readText())
}

fun loadJsonConfig(r : Reader) : Map<String, Any?> {
    return JsonSlurper().parse(r) as Map<String, Any?>
}

fun loadDefaultConfig() : ConfigObject {
    return ConfigSlurper().parse(javaClass<Configuration>().getResource("defaultConfig.groovy").readText(ENCODING))
}

fun getJsonConfigFile(userConfigFile : File) : File {
    return File(userConfigFile.getParentFile(), JSON_CONFIG_FILENAME)
}

fun getSettingType(name : String, knownSettings : Map<String, Any>) : Class<*>? {
    val type = knownSettings[name] ?: knownSettings[makeWildcard(name)]
    return if (type != null) type as Class<*> else null
}

fun makeWildcard(dottedString : String) : String {
    if (dottedString.indexOf(NAME_SEPARATOR) == -1) return dottedString
    else return dottedString.split(NAME_SEPARATOR_REGEX.toRegex()).allButLast().join(NAME_SEPARATOR) + ".*"
}

fun matchingSetting(name : String, knownSettings : Map<String, Any>) : Map.Entry<String, Any>? {
    return knownSettings.asSequence().firstOrNull { entry ->
        entry.key == name || settingNameAsRegex(entry.key).toRegex().hasMatch(name)
    }
}

fun settingNameAsRegex(name : String) : String {
    return name.replace(NAME_SEPARATOR, NAME_SEPARATOR_REGEX).replace("*", "[\\w]+")
}

/**
 * Checks whether
 * @param name
 * @param knownSettings
 * @param value
 * @return
 */
fun validateSetting(name : String, knownSettings : Map<String, Any>, value : Any) : Boolean {
    val setting = matchingSetting(name, knownSettings)
    if (setting == null) throw UnknownSettingException(name)

    val converter = Converters.requireConverter(setting.value as Class<*>)
    return value == null || converter.validate(value)
}

/**
 * <p>Takes a dot-separated string, such as "test.report.dir", and gets the corresponding
 * config object property, {@code root.test.report.dir}.</p>
 * @param root The config object to retrieve the value from.
 * @param dottedString The dot-separated string representing a configuration option.
 * @return The required configuration value, or {@code null} if the setting doesn't exist.
 */
private fun getConfigOption(root : Map<String, Any>, dottedString : String) : Any? {
    val parts = dottedString.split(NAME_SEPARATOR_REGEX.toRegex())
    if (parts.size() == 1) return root.get(parts[0])

    val firstParts = parts.allButLast()
    val configEntry = firstParts.fold(root as Map<String, Any?>?) { config, keyPart ->
        val value = config?.get(keyPart)
        if (value != null) value as Map<String, Any?> else null
    }

    return configEntry?.get(parts.last())
}

private fun getConfigOptionAsList(root : ConfigObject, dottedString : String) : MutableList<Any> {
    val initialValue = getConfigOption(root as Map<String, Any>, dottedString)
    val newValue = if (initialValue is Collection<*>)
        ArrayList<Any>(initialValue)
    else if (initialValue != null)
        arrayListOf(initialValue)
    else ArrayList<Any>()

    setConfigOption(root, dottedString, newValue)
    return newValue
}

/**
 * <p>Takes a dot-separated string, such as "test.report.dir", and sets the corresponding
 * config object property, {@code root.test.report.dir}, to the given value.</p>
 * <p><em>Note</em> the {@code @CompileDynamic} annotation is currently required due to
 * issue <a href="https://jira.codehaus.org/browse/GROOVY-6480">GROOVY-6480</a>.</p>
 * @param root The config object to set the value on.
 * @param dottedString The dot-separated string representing a configuration option.
 * @param value The new value for this option.
 * @return The map containing the final part of the dot-separated string as a
 * key. In other words, {@code retval.dir == value} for the dotted string example above.
 */
private fun setConfigOption(root : ConfigObject, dottedString : String, value : Any) : Map<String, Any> {
    val parts = dottedString.split(NAME_SEPARATOR_REGEX.toRegex())
    val firstParts = parts.subList(0, parts.size() - 1)
    val configEntry = firstParts.fold(root) { config, keyPart ->
        config.getProperty(keyPart) as ConfigObject
    }

    configEntry.setProperty(parts.last(), value)
    return configEntry as Map<String, Any>
}

private fun clearConfigOption(root : ConfigObject, dottedString : String) {
    val parts = dottedString.split(NAME_SEPARATOR_REGEX.toRegex())
    val configParts = parts.subList(0, parts.size() - 1)
            .fold(arrayListOf(root) as MutableList<ConfigObject>) { list, namePart ->
        list.add(list.last().getProperty(namePart) as ConfigObject)
        list
    }

    configParts.last().remove(parts.last())
    if (parts.size() == 1) return

    for (i in parts.size()-2..0) {
        if (configParts[i].getProperty(parts[i]) != null) break
        configParts[i].remove(parts[i])
    }
}

private fun loadConfigFromSystemProperties(overrideConfig : ConfigObject) : Map<String, String> {
    val props = (System.getProperties() as Map<String, String>).filter {
        it.key.startsWith(SYSPROP_OVERRIDE_PREFIX)
    }

    props.forEach { entry ->
        val settingName = entry.key.substringAfter(SYSPROP_OVERRIDE_PREFIX)

        if (!validateSetting(settingName, VALID_OPTIONS, entry.value)) {
            log.warning("Unknown option '$settingName' or its values are invalid: ${entry.value}")
        }

        setConfigOption(overrideConfig, settingName, entry.value)
    }

    return props
}

/**
 * Adds the data from a map to a given Groovy configuration object. This
 * works recursively, so any values in the source map that are maps themselves
 * are treated as a set of sub-keys in the configuration.
 */
private fun addConfigEntries(data : Map<String, Any>, obj : ConfigObject) {
    data.forEach { entry ->
        if (entry.value is Map<*, *>) {
            addConfigEntries(entry.value as Map<String, Any>, obj.getProperty(entry.key) as ConfigObject)
        }
        else obj.setProperty(entry.key, entry.value)
    }
}

/**
 * <p>Takes two maps and works out which keys are shared between two maps.
 * Crucially, this method recurses such that it checks the keys of any
 * nested maps as well. For example, if two maps have the same keys and
 * sub-keys such as [one: [two: "test"]], then the key "one.two" is
 * considered to be a shared key. Note how the keys of sub-maps are
 * referenced using dot ('.') notation.</p>
 * @param map1 The first map.
 * @param map2 The map to compare against the first map.
 * @return A list of the keys that both maps share. If either map is empty
 * or the two share no keys at all, this method returns an empty list.
 * Sub-keys are referenced using dot notation ("my.option.override") in the
 * same way that <tt>ConfigObject</tt> keys are converted when the settings
 * are flattened.
 */
private fun findIntersectKeys(map1 : Map<String, Any>, map2 : Map<String, Any>) : List<String> {
    val keys = map1.keySet().intersect(map2.keySet())
    val result : MutableList<String> = keys.toArrayList()

    for (k in keys) {
        if (map1[k] is Map<*, *> && map2[k] is Map<*, *>) {
            result.remove(k)
            result.addAll(findIntersectKeys(
                    map1[k] as Map<String, Any>,
                    map2[k] as Map<String, Any>).map { k + NAME_SEPARATOR + it })
        }
    }

    return result
}

fun List<T>.allButLast<T>() : List<T> {
    return this.take(this.size() - 1)
}
