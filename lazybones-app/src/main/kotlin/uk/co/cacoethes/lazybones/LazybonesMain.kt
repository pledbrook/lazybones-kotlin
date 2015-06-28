package uk.co.cacoethes.lazybones

import joptsimple.OptionException
import joptsimple.OptionParser
import joptsimple.OptionSet
import uk.co.cacoethes.lazybones.commands.Commands
import uk.co.cacoethes.lazybones.config.Configuration
import uk.co.cacoethes.lazybones.util.isUrl
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.net.URL
import java.util.*
import java.util.jar.Manifest
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

val USAGE = "USAGE: lazybones [OPTIONS] [COMMAND]\n"

fun main(args: Array<String>) {
    val config = Configuration.initConfiguration()

    val parser : OptionParser = OptionParserBuilder.makeOptionParser()
    val optionSet : OptionSet
    try {
        optionSet = parser.parse(*args)
    }
    catch (ex : OptionException) {
        // Logging not initialised yet, so use println()
        println(getHelp(ex.getMessage() ?: "<unknown>", parser))
        System.exit(1)
        return
    }

    // Create a map of options from the "options.*" key in the user's
    // configuration and then add any command line options to that map,
    // overriding existing values.
    val globalOptions : MutableMap<String, Any?> =
            config.getSubSettings("options") as MutableMap<String, Any?>?
                    ?: hashMapOf<String, Any?>()
    for (spec in optionSet.specs()) {
        val valueList = spec.values(optionSet) ?: ArrayList<Any>()
        globalOptions[spec.options().iterator().next()] = if (!valueList.isEmpty()) valueList[0] else true
    }

    if (optionSet.has(Options.VERSION)) {
        println("Lazybones version ${readVersion()}")
        System.exit(0)
        return
    }

    // We're now ready to initialise the logging system as we have the global
    // options parsed and available.
    initLogging(globalOptions)

    // Determine the command to run and its argument list.
    val cmd : String
    var argsList = optionSet.nonOptionArguments()

    if (argsList.size() == 0 || optionSet.has(Options.HELP_SHORT)) {
        cmd = "help"
    }
    else {
        cmd = argsList.first()
        argsList = argsList.slice(1..-1)
    }

    validateConfig(config)

    // Execute the corresponding command
    val cmdInstance = Commands.getAll(config).firstOrNull { it.getName() == cmd }
    if (cmdInstance != null) {
        Logger.getLogger("uk.co.cacoethes.lazybones.LazybonesMain").severe("There is no command '" + cmd + "'")
        System.exit(1)
        return
    }

    // TODO Default 0 becomes unnecessary when Command is converted to Kotlin
    System.exit(cmdInstance?.execute(argsList, globalOptions, config) ?: 0)
}

fun readVersion() : String {
    // First find the MANIFEST.MF for the JAR containing this class
    //
    // Can't use this.getResource() since that looks for a static method
    val cls = javaClass<LazybonesScript>()
    val classPath = cls.getResource(cls.getSimpleName() + ".class").toString()
    if (!classPath.startsWith("jar")) return "unknown"

    val manifestPath = classPath.substringBeforeLast("!") + "/META-INF/MANIFEST.MF"

    // Now read the manifest and extract Implementation-Version to get the
    // Lazybones version.
    val manifest = Manifest(URL(manifestPath).openStream())
    return manifest.getMainAttributes().getValue("Implementation-Version")
}

fun validateConfig(config : Configuration) : Unit {
    config.getSubSettings("templates.mappings").forEach { entry ->
        if (!isUrl(entry.value as String)) {
            throw IllegalArgumentException("the value [${entry.value}] for mapping [${entry.key}] is not a url")
        }
    }
}

fun initLogging(options : Map<String, Any?>) : Unit {
    // Load a basic logging configuration from a string containing Java
    // properties syntax.
    val inputStream = ByteArrayInputStream(LOG_CONFIG.toByteArray(Configuration.getENCODING()))
    LogManager.getLogManager().readConfiguration(inputStream)

    // Update logging level based on the global options. We temporarily
    // get hold of the parent logger of all Lazybones loggers so that all
    // child loggers are updated (as the child loggers inherit the level
    // from this parent).
    val parentLogger = Logger.getLogger("uk.co.cacoethes.lazybones")

    if (options[Options.VERBOSE_SHORT] as Boolean) parentLogger.setLevel(Level.FINEST)
    else if (options[Options.QUIET] as Boolean) parentLogger.setLevel(Level.WARNING)
    else if (options[Options.INFO] as Boolean) parentLogger.setLevel(Level.INFO)
    else if (options[Options.LOG_LEVEL] != null) {
        try {
            parentLogger.setLevel(Level.parse(options[Options.LOG_LEVEL].toString()))
        }
        catch (ex : IllegalArgumentException) {
            parentLogger.severe("Invalid log level provided: ${ex.getMessage()}")
            System.exit(1)
        }
    }
}

/**
 * Returns a help string to display for usage. It incorporates the given
 * message, the command's usage string, and the supported JOptSimple options.
 */
fun getHelp(message : String, parser : OptionParser) : String {
    val writer = StringWriter()
    parser.printHelpOn(writer)

    return """\
${message}

${USAGE}
${writer}"""
}

/**
 * Logging configuration in Properties format. It simply sets up the console
 * handler with a formatter that just prints the message without any decoration.
 */
private val LOG_CONFIG = """\
# Logging
handlers = java.util.logging.ConsoleHandler

# Console logging
java.util.logging.ConsoleHandler.formatter = PlainFormatter
java.util.logging.ConsoleHandler.level = FINEST
"""