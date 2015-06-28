package uk.co.cacoethes.lazybones.commands

import groovy.util.logging.Log
import joptsimple.OptionParser
import joptsimple.OptionSet
import uk.co.cacoethes.lazybones.LazybonesScriptException
import uk.co.cacoethes.lazybones.NoVersionsFoundException
import uk.co.cacoethes.lazybones.PackageNotFoundException
import uk.co.cacoethes.lazybones.config.Configuration
import uk.co.cacoethes.lazybones.packagesources.PackageSource
import uk.co.cacoethes.lazybones.packagesources.PackageSourceBuilder
import uk.co.cacoethes.lazybones.scm.GitAdapter
import uk.co.cacoethes.lazybones.util.unzip
import wslite.http.HTTPClientException
import java.io.File
import java.util.*

import java.util.logging.Level

/**
 * Implements Lazybone's create command, which creates a new project based on
 * a specified template.
 */
class CreateCommand(cacheDir : File, val mappings : Map<String, String> = hashMapOf()) : AbstractCommand() {
    val USAGE = """\
USAGE: create <template> <version>? <dir>

  where  template = The name of the project template to use.
         version  = (optional) The version of the project template to use. Uses
                    the latest version of the template by default.
         dir      = The name of the directory in which to create the project
                    structure. This can be '.' to mean 'in the current
                    directory.'
"""
    private val README_BASENAME = "README"
    private val SPACES_OPT = "spaces"
    private val VAR_OPT = "P"
    private val GIT_OPT = "with-git"

    val packageSourceFactory : PackageSourceBuilder
    val packageLocationFactory : PackageLocationBuilder
    val packageDownloader : PackageDownloader
//    val mappings : Map<String, String>

    init {
        packageSourceFactory = PackageSourceBuilder()
        packageLocationFactory = PackageLocationBuilder(cacheDir)
        packageDownloader = PackageDownloader()
    }

    constructor(config : Configuration) : this(
            File(config.getSetting("cache.dir") as String),
            config.getSetting("templates.mappings") as Map<String, String>)

    override fun getName() : String { return "create" }

    override fun getDescription() : String {
        return "Creates a new project from a template."
    }

    override protected fun doAddToParser(parser : OptionParser) : OptionParser {
        parser.accepts(SPACES_OPT, "Sets the number of spaces to use for indent in files.").withRequiredArg()
        parser.accepts(VAR_OPT, "Add a substitution variable for file filtering.").withRequiredArg()
        parser.accepts(GIT_OPT, "Creates a git repository in the new project.")
        return parser
    }

    override protected val parameterRange = 2..3  // Either a directory or a version + a directory

    override protected val usage = USAGE

    override protected fun doExecute(
            cmdOptions : OptionSet,
            globalOptions : Map<*, *>,
            configuration : Configuration) : Int {
        try {
            val createData = evaluateArgs(cmdOptions)

            val packageSources = packageSourceFactory.buildPackageSourceList(configuration)
            val packageLocation = packageLocationFactory.buildPackageLocation(
                    createData.packageArg.templateName,
                    createData.requestedVersion,
                    packageSources)
            val pkg = packageDownloader.downloadPackage(
                    packageLocation,
                    createData.packageArg.templateName,
                    createData.requestedVersion)

            val targetDir = createData.targetDir.getCanonicalFile()
            targetDir.mkdirs()
            unzip(pkg, targetDir)

            val scmAdapter = if (cmdOptions.has(GIT_OPT)) GitAdapter(configuration) else null

            val executor = InstallationScriptExecuter(scmAdapter)
            executor.runPostInstallScriptWithArgs(
                    cmdOptions.valuesOf(VAR_OPT).fold(HashMap()) { m: MutableMap<String, String>, pair : Any? ->
                        val keyValue = pair!!.toString().split('=')
                        m[keyValue[0]] = m[keyValue[1]]!!
                        m
                    },
                    createData.packageArg.qualifiers,
                    targetDir)

            logReadme(createData)

            logSuccess(createData)

            return 0
        }
        catch (ex : PackageNotFoundException) {
            if (ex.getVersion() != null && ex.getVersion().isNotEmpty()) {
                log.severe("Cannot find version ${ex.getVersion()} of template '${ex.getName()}'. " +
                        "Project has not been created.")
            }
            else {
                log.severe("Cannot find a template named '${ex.getName()}'. Project has not been created.")
            }
            return 1
        }
        catch (ex : NoVersionsFoundException) {
            log.severe("No version of '${ex.getPackageName()}' has been published. This can also happen if " +
                    "the latest version on Bintray is 'null'.")
            return 1
        }
        catch (ex : HTTPClientException) {
            if (OfflineMode.isOffline(ex)) {
                OfflineMode.printlnOfflineMessage(ex, log, globalOptions["stacktrace"] as Boolean? ?: false)
            }
            else {
                log.severe("Unexpected failure: ${ex.getMessage()}")
                if (globalOptions["stacktrace"] as Boolean? ?: false) log.log(Level.SEVERE, "", ex)
            }

            println()
            println("Cannot create a new project when the template isn't locally cached or no version is specified")
            return 1
        }
        catch (ex : LazybonesScriptException) {
            log.severe("Post install script caused an exception, project might be corrupt: " +
                    ex.getCause()?.getMessage())
            log.severe("The unpacked template will remain in place to help you diagnose the problem")

            if (globalOptions["stacktrace"] as Boolean? ?: false) {
                log.log(Level.SEVERE, "", ex.getCause())
            }

            return 1
        }
        catch (all : Throwable) {
            log.log(Level.SEVERE, "", all)
            return 1
        }
    }

    protected fun evaluateArgs(commandOptions : OptionSet) : CreateCommandInfo {
        val mainArgs = commandOptions.nonOptionArguments()
        val createCmdInfo = getCreateInfoFromArgs(mainArgs)

        logStart(createCmdInfo.packageArg.templateName, createCmdInfo.requestedVersion, createCmdInfo.targetDir)

        return createCmdInfo
    }

    protected fun getCreateInfoFromArgs(mainArgs : List<String>) : CreateCommandInfo {

        val packageName = TemplateArg(mappings[mainArgs[0]] ?: mainArgs[0])

        if (hasVersionArg(mainArgs)) {
            return CreateCommandInfo(packageName, mainArgs[1], toFile(mainArgs[2]))
        }

        return CreateCommandInfo(packageName, "", toFile(mainArgs[1]))
    }

    protected fun hasVersionArg(args : List<String>) : Boolean {
        return args.size() == 3
    }

    private fun logStart(packageName : String, version : String?, targetPath : File) {
        if (log.isLoggable(Level.INFO)) {
            log.info("Creating project from template " + packageName + ' ' +
                    (version ?: "(latest)") + " in " +
                    if(isPathCurrentDirectory(targetPath)) "current directory" else "'${targetPath}'")
        }
    }

    private fun logSuccess(createData : CreateCommandInfo) {
        log.info("")
        log.info("Project created in " + (if (isPathCurrentDirectory(createData.targetDir))
            "current directory" else createData.targetDir.path) + '!')
    }

    private fun logReadme(createData : CreateCommandInfo) {
        // Find a suitable README and display that if it exists.
        val readmeFiles = createData.targetDir.listFiles({ dir, name ->
            name == README_BASENAME || name.startsWith(README_BASENAME)
        })

        log.info("")
        if (readmeFiles.isEmpty()) log.info("This project has no README")
        else log.info(readmeFiles[0].readText(Charsets.UTF_8))
    }

    private fun isPathCurrentDirectory(path : File) : Boolean {
        return path.canonicalPath == File("").canonicalPath
    }

    /**
     * Converts a string file path to a `File` instance. Its unique behaviour
     * is that the path "." is translated to "", i.e. the empty path.
     */
    private fun toFile(path : String) : File {
        return File(if (path == ".") "" else path)
    }
}
