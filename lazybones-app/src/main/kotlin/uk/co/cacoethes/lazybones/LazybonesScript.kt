package uk.co.cacoethes.lazybones

import groovy.io.FileType
import groovy.lang.Script
import groovy.lang.Writable
import groovy.text.SimpleTemplateEngine
import groovy.text.TemplateEngine
import org.apache.commons.io.FilenameUtils
import uk.co.cacoethes.lazybones.util.AntPathMatcher
import uk.co.cacoethes.lazybones.util.NameType
import uk.co.cacoethes.lazybones.util.Naming
import java.io.*

import java.lang.reflect.Method
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Base script that will be applied to the lazybones.groovy root script in a lazybones template
 *
 * @author Tommy Barker
 */
class LazybonesScript : Script() {
    val log = Logger.getLogger(this.javaClass.getName())
    val DEFAULT_ENCODING = "utf-8"

    /**
     * The target project directory. For project templates, this will be the
     * same as the directory into which the template was installed, i.e.
     * {@link #templateDir}. But for subtemplates, this will be the directory
     * of the project that the <code>generate</code> command is running in.
     */
    var projectDir : File? = null

    /**
     * The location of the unpacked template. This will be the same as
     * {@link #projectDir} for project templates, but will be different for
     * subtemplates. This is the base path used when searching for files
     * that need filtering.
     * @since 0.7
     */
    var templateDir : File? = null

    /**
     * Stores the values for {@link #ask(java.lang.String, java.lang.Object, java.lang.String)}
     * calls when a parameter name is specified.
     * @since 0.7
     */
    val parentParams : MutableMap<String, Any?> = hashMapOf()

    /**
     * @since 0.7
     */
    var tmplQualifiers : List<String> = arrayListOf()

    /**
     * The encoding/charset used by the files in the template. This is UTF-8
     * by default.
     */
    var fileEncoding = DEFAULT_ENCODING

    var scmExclusionsFile : File? = null

    /**
     * The reader stream from which user input will be pulled. Defaults to a
     * wrapper around stdin using the platform's default encoding/charset.
     */
    var reader = BufferedReader(InputStreamReader(System.`in`))

    private var templateEngine : TemplateEngine? = SimpleTemplateEngine()

    private val registeredEngines : MutableMap<String, TemplateEngine> = hashMapOf()

    private val antPathMatcher = AntPathMatcher().pathSeparator(System.getProperty("file.separator"))

    /**
     * Provides access to the script's logger.
     * @since 0.9
     */
//    val getLog() : Logger { return this.log }

    /**
     * Declares the list of file patterns that should be excluded from SCM.
     * @since 0.5
     */
    fun scmExclusions(vararg exclusions : String) : Unit {
        if (scmExclusionsFile == null) return

        log.fine("Writing SCM exclusions file with: ${exclusions}")
        scmExclusionsFile!!.printWriter().use { writer : PrintWriter ->
            for (exclusion in exclusions) {
                writer.println(exclusion)
            }
        }
    }

    /**
     * Converts a name from one convention to another, e.g. from camel case to
     * natural form ("TestString" to "Test String").
     * @param args Both {@code from} and {@code to} arguments are required and
     * must be instances of {@link NameType}.
     * @param name The string to convert.
     * @return The converted string, or {@code null} if the given name is {@code
     * null}, or an empty string if the given string is empty.
     * @since 0.5
     */
    fun transformText(args : Map<String, Any?>, name : String) : String {
        if (!(args["from"] is NameType)) {
            throw IllegalArgumentException("Invalid or no value for 'from' named argument: ${args["from"]}")
        }

        if (!(args["to"] is NameType)) {
            throw IllegalArgumentException("Invalid or no value for 'to' named argument: ${args["to"]}")
        }

        return Naming.convert(args["from"] as NameType, args["to"] as NameType, name)
    }

    /**
     * Registers a template engine against a file suffix. For example, you can
     * register a {@code HandlebarsTemplateEngine} instance against the suffix
     * "hbs" which would result in *.hbs files being processed by that engine.
     * @param suffix The file suffix, excluding the dot ('.')
     * @param engine An instance of Groovy's {@code TemplateEngine}, e.g.
     * {@code SimpleTemplateEngine}.
     * @since 0.6
     */
    fun registerEngine(suffix : String, engine : TemplateEngine) : Unit {
        this.registeredEngines[suffix] = engine
    }

    /**
     * Registers a template engine as the default. This template engine will be
     * used by {@link #processTemplates(java.lang.String, java.util.Map)} for
     * files that don't have a template-specific suffix. The normal default is
     * an instance of Groovy's {@code SimpleTemplateEngine}.
     * @param engine An instance of Groovy's {@code TemplateEngine}, e.g.
     * {@code SimpleTemplateEngine}. Cannot be {@code null}. Use
     * {@link #clearDefaultEngine()} if you want to disable the default engine.
     * @since 0.6
     */
    fun registerDefaultEngine(engine : TemplateEngine) : Unit {
        this.templateEngine = engine
    }

    /**
     * Disables the default template engine. The result is that
     * {@link #processTemplates(java.lang.String, java.util.Map)} will simply
     * ignore any files that don't have a registered suffix (via
     * {@link #registerEngine(java.lang.String, groovy.text.TemplateEngine)}),
     * even if they match the given pattern.
     * @since 0.6
     */
    fun clearDefaultEngine() : Unit {
        this.templateEngine = null
    }

    /**
     * Prints a message asking for a property value.  If the user has no response the default
     * value will be returned.  null can be returned
     *
     * @param message
     * @param defaultValue
     * @return the response
     * @since 0.4
     */
    fun ask(message : String, defaultValue : String? = null) : String? {
        print(message)
        return reader.readLine() ?: defaultValue
    }

    /**
     * <p>Prints a message asking for a property value.  If a value for the property already exists in
     * the binding of the script, it is used instead of asking the question.  If the user just presses
     * &lt;return&gt; the default value is returned.</p>
     * <p>This method also saves the value in the script's {@link #parentParams} map against the
     * <code>propertyName</code> key.</p>
     *
     * @param message The message to display to the user requesting some information.
     * @param defaultValue If the user doesn't provide a value, return this.
     * @param propertyName The name of the property in the binding whose value will
     * be used instead of prompting the user for input if that property exists.
     * @return The required value based on whether the message was displayed and
     * whether the user entered a value.
     * @since 0.4
     */
    fun ask(message : String, defaultValue : String?, propertyName : String) : String? {
        val value = if (propertyName.isNotBlank() && getBinding().hasVariable(propertyName))
            getBinding().getVariable(propertyName)?.toString()
        else
            ask(message, defaultValue)

        parentParams[propertyName] = value
        return value
    }

    /**
     * Been deprecated as of lazybones 0.5, please use
     * {@link LazybonesScript#processTemplates(java.lang.String, java.util.Map)}
     *
     * @deprecated
     * @param filePattern
     * @param substitutionVariables
     * @since 0.4
     */
    fun filterFiles(filePattern : String, substitutionVariables : Map<String, Any?>) : Unit {
        val warningMessage = "The template you are using depends on a deprecated part of the API, [filterFiles], " +
                "which will be removed in Lazybones 1.0. Use a version of Lazybones prior to 0.5 with this template."
        log.warning(warningMessage)
        processTemplates(filePattern, substitutionVariables)
    }

    /**
     * @param filePattern classic ant pattern matcher
     * @param substitutionVariables model for processing the template
     * @return
     * @since 0.5
     */
    fun processTemplates(filePattern : String, substitutionVariables : Map<String, Any?>) : Script {
        if (projectDir == null) throw IllegalStateException("projectDir has not been set")
        if (templateDir == null) throw IllegalStateException("templateDir has not been set")

        var atLeastOneFileFiltered = false

        if (templateEngine != null) {
            log.fine("Processing files matching the pattern ${filePattern} using the default template engine")
            atLeastOneFileFiltered = processTemplatesWithEngine(
                    findFilesByPattern(filePattern),
                    substitutionVariables,
                    templateEngine!!,
                    true) || atLeastOneFileFiltered
        }

        for (entry in registeredEngines) {
            log.fine("Processing files matching the pattern ${filePattern} using the " +
                    "template engine for '${entry.key}'")
            atLeastOneFileFiltered = processTemplatesWithEngine(
                    findFilesByPattern(filePattern + '.' + entry.key),
                    substitutionVariables,
                    entry.value,
                    false) || atLeastOneFileFiltered
        }

        if (!atLeastOneFileFiltered) {
            log.warning("No files filtered with file pattern [$filePattern] " +
                    "and template directory [${templateDir!!.path}]")
        }

        // Not sure why this is here, but just in case someone actually relies on it...
        return this
    }

    /**
     * Returns a flat list of files in the target directory that match the
     * given Ant path pattern. The pattern should use forward slashes rather
     * than the platform file separator.
     */
    private fun findFilesByPattern(pattern : String) : List<File> {
        val filePatternWithUserDir = File(templateDir!!.getCanonicalFile(), pattern).path

        return templateDir!!.walkTopDown().filter {
            antPathMatcher.match(filePatternWithUserDir, it.canonicalPath)
        }.asSequence().toList()
    }

    /**
     * Applies a specific template engine to a set of files. The files should be
     * templates of the appropriate type.
     * @param file The template files to process.
     * @param properties The model (variables and their values) for the templates.
     * @param engine The template engine to use for processing.
     * @param replace If {@code true}, replaces each source file with the text
     * generated by the processing. Otherwise, a new file is created with the same
     * name as the original, minus its final suffix (assumed to be a template-specific
     * suffix).
     * @throws IllegalArgumentException if any of the template file don't exist.
     */
    protected fun processTemplatesWithEngine(
            files : List<File>,
            properties : Map<String, Any?>,
            engine : TemplateEngine,
            replace : Boolean) : Boolean {

        return files.fold(false) { filtered, file ->
            processTemplateWithEngine(file, properties, engine, replace)
            return true
        }
    }

    /**
     * Applies a specific template engine to a file. The file should be a
     * template of the appropriate type.
     * @param file The template file to process.
     * @param properties The model (variables and their values) for the template.
     * @param engine The template engine to use for processing.
     * @param replace If {@code true}, replaces the file with the text generated
     * by the processing. Otherwise, a new file is created with the same name as
     * the original, minus its final suffix (assumed to be a template-specific suffix).
     * @throws IllegalArgumentException if the template file doesn't exist.
     */
    protected fun processTemplateWithEngine(
            file : File,
            properties : Map <String, Any?>,
            engine : TemplateEngine,
            replace : Boolean) : Unit {
        if (!file.exists()) {
            throw IllegalArgumentException("File ${file} does not exist")
        }

        log.fine("Filtering file ${file}${if (replace) " (replacing)" else ""}")

        val template = makeTemplate(engine, file, properties)

        val targetFile = if (replace) file else File(file.getParentFile(), FilenameUtils.getBaseName(file.path))
        BufferedWriter(OutputStreamWriter(targetFile.outputStream(), fileEncoding)).use { writer ->
            template.writeTo(writer)
        }

        if (!replace) file.delete()
    }

    override fun run() : Any {
        throw UnsupportedOperationException("${this.javaClass.getName()} is not meant to be used directly. " +
                "It should instead be used as a base script")
    }

    /**
     * Determines whether the version of Lazybones loading the post-installation
     * script supports a particular feature. Current features include "ask" and
     * processTemplates for example.
     * @param featureName The name of the feature you want to check for. This should
     * be the name of a method on `LazybonesScript`.
     * @since 0.4
     */
    fun hasFeature(featureName : String) : Boolean {
        return this.javaClass.getMethods().any { method -> method.getName() == featureName }
    }

    /**
     * Returns the target project directory as a string. Only kept for backwards
     * compatibility and post-install scripts should switch to using {@link #projectDir}
     * as soon as possible.
     * @deprecated Will be removed before Lazybones 1.0
     */
    fun getTargetDir() : String {
        log.warning("The targetDir property is deprecated and should no longer be used by post-install scripts. " +
                "Use `projectDir` instead.")
        return projectDir!!.path
    }

    /**
     * Read-only access to the path matcher. This method seems to be required
     * for {@link #processTemplates(java.lang.String, java.util.Map)} to work
     * properly.
     */
//    protected AntPathMatcher getAntPathMatcher() { return this.antPathMatcher }

    /**
     * Creates a new template instance from a file.
     * @param engine The template engine to use for parsing the file contents.
     * @param file The file to use as the content of the template.
     * @param properties The properties to populate the template with. Each key
     * in the map should correspond to a variable in the template.
     * @return The new template object
     */
    protected fun makeTemplate(engine : TemplateEngine, file : File, properties : Map<String, Any?>) : Writable {
        BufferedReader(InputStreamReader(file.inputStream(), fileEncoding)).use { reader ->
            return engine.createTemplate(reader).make(properties)
        }
    }
}
