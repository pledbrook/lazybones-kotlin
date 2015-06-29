package uk.co.cacoethes.lazybones.commands

import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.Script
import groovy.text.SimpleTemplateEngine
import groovy.util.logging.Log
import org.codehaus.groovy.control.CompilerConfiguration
import uk.co.cacoethes.lazybones.LazybonesScript
import uk.co.cacoethes.lazybones.LazybonesScriptException
import uk.co.cacoethes.lazybones.readVersion
import uk.co.cacoethes.lazybones.scm.ScmAdapter
import java.io.*
import java.util.*

/**
 * Sets up and runs a post-install script, managing properties provided by
 * parent templates, SCM integration, and setting the appropriate script base
 * class.
 */
class InstallationScriptExecuter(val scmAdapter : ScmAdapter?) {
    val STORED_PROPS_FILENAME = "stored-params.properties"
    val FILE_ENCODING = "UTF-8"

    constructor() : this(null)

    public fun runPostInstallScriptWithArgs(
            variables : Map<String, Any?>,
            tmplQualifiers : List<String>,
            targetDir : File,
            templateDir : File = targetDir) {
        // Run the post-install script if it exists. The user can pass variables
        // to the script via -P command line arguments. This also places
        // lazybonesVersion, lazybonesMajorVersion, and lazybonesMinorVersion
        // variables into the script binding.
        try {
            val scriptVariables = HashMap(variables)
            scriptVariables.putAll(loadParentParams(templateDir))
            scriptVariables.putAll(evaluateVersionScriptVariables())
            runPostInstallScript(tmplQualifiers, targetDir, templateDir, scriptVariables)
            initScmRepo(targetDir.getAbsoluteFile())
        }
        catch (all : Throwable) {
            throw LazybonesScriptException(all)
        }
    }

    /**
     * Runs the post install script if it exists in the unpacked template
     * package. Once the script has been run, it is deleted.
     * @param targetDir the target directory that contains the lazybones.groovy script
     * @param model a map of variables available to the script
     * @return the lazybones script if it exists, otherwise {@code null}.
     */
    fun runPostInstallScript(
            tmplQualifiers: List<String>,
            targetDir : File,
            templateDir : File,
            model : Map<String, Any?>) : Script? {
        val installScriptFile = File(templateDir, "lazybones.groovy")
        if (installScriptFile.exists()) {
            val script = initializeScript(model, tmplQualifiers, installScriptFile, targetDir, templateDir)
            script.run()
            installScriptFile.delete()

            persistParentParams(templateDir, script)
            return script
        }

        return null
    }

    protected fun initializeScript(
            model : Map<String, Any?>,
            tmplQualifiers : List<String>,
            scriptFile : File,
            targetDir : File,
            templateDir : File) : LazybonesScript {
        val compiler = CompilerConfiguration()
        compiler.setScriptBaseClass(javaClass<LazybonesScript>().getName())

        // Can't use 'this' here because the static type checker does not
        // treat it as the class instance:
        //       https://jira.codehaus.org/browse/GROOVY-6162
        val shell = GroovyShell(this.javaClass.getClassLoader(), Binding(model), compiler)

        // Setter methods must be used here otherwise the physical properties on the
        // script object won't be set. I can only assume that the properties are added
        // to the script binding instead.
        val script = shell.parse(scriptFile) as LazybonesScript
        val groovyEngine = SimpleTemplateEngine()
        script.registerDefaultEngine(groovyEngine)
        script.registerEngine("gtpl", groovyEngine)
        script.tmplQualifiers = tmplQualifiers
        script.projectDir = targetDir
        script.templateDir = templateDir
        script.scmExclusionsFile = if (scmAdapter != null) File(targetDir, scmAdapter.getExclusionsFilename()) else null
        return script
    }

    protected fun persistParentParams(dir : File, script : LazybonesScript) {
        // Save this template's named parameters in a file inside a .lazybones
        // sub-directory of the unpacked template.
        val lzbDir = File(dir, ".lazybones")
        lzbDir.mkdirs()
        BufferedWriter(OutputStreamWriter(File(lzbDir, STORED_PROPS_FILENAME).outputStream(), FILE_ENCODING)).use { w ->
            // Need to use the getter method explicitly, otherwise it seems to
            // return an empty map.
            script.parentParams.mapValues { v ->
                v.toString()
            }.toProperties().store(w, "Lazybones saved template parameters")
        }
    }

    protected fun loadParentParams(templateDir : File) : Map<String, Any> {
        // Use the unpacked template's directory as the reference point and
        // then treat its parent directory as the location for the stored
        // parameters. If `templateDir` is CWD, then the parent directory will
        // actually be null, in which case there is no stored parameters file
        // (for example in the case of an unpacked project template rather
        // than a subtemplate).
        val lzbDir = templateDir.getParentFile()
        if (lzbDir == null) return hashMapOf()

        val paramsFile = File(lzbDir, STORED_PROPS_FILENAME)
        val props = Properties()
        if (paramsFile.exists()) {
            BufferedReader(InputStreamReader(paramsFile.inputStream(), FILE_ENCODING)).use { r -> props.load(r) }
        }

        return hashMapOf("parentParams" to props)
    }

    /**
     * Reads the Lazybones version, breaks it up, and adds {@code lazybonesVersion},
     * {@code lazybonesMajorVersion}, and {@code lazybonesMinorVersion} variables
     * to a map that is then returned.
     */
    protected fun evaluateVersionScriptVariables() : Map <String, Any> {
        val version = readVersion()
        val vars : MutableMap<String, Any> = hashMapOf("lazybonesVersion" to version)

        val versionParts = version.split("""[\.\-]""".toRegex())
        assert(versionParts.size() > 1)
        assert(versionParts.all { it.length() > 0 })

        vars["lazybonesMajorVersion"] = versionParts[0].toInt()
        vars["lazybonesMinorVersion"] = versionParts[1].toInt()

        return vars
    }

    private fun initScmRepo(location : File) {
        if (scmAdapter != null) {
            scmAdapter.initializeRepository(location)
            scmAdapter.commitInitialFiles(location, "Initial commit")
        }
    }
}
