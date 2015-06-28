package uk.co.cacoethes.lazybones.commands

import groovy.util.logging.Log
import joptsimple.OptionParser
import joptsimple.OptionSet
import org.apache.commons.io.FileUtils
import uk.co.cacoethes.lazybones.config.Configuration
import uk.co.cacoethes.lazybones.LazybonesScriptException
import uk.co.cacoethes.lazybones.PackageNotFoundException
import uk.co.cacoethes.lazybones.util.unzip
import java.io.File
import java.util.*

import java.util.logging.Level

/**
 * Implements Lazybone's generate command, which processes subtemplates in a
 * Lazybones-created project. The command unpacks the subtemplate into the
 * project's .lazybones directory and runs the post-install script. It's up
 * to that script to create directories and files in main project source tree.
 */
class GenerateCommand : AbstractCommand() {
    val LAZYBONES_DIR = File(".lazybones")

    val USAGE = """\
USAGE: generate <template>

  where  template = The name of the subtemplate to use.
"""
    private val SPACES_OPT = "spaces"
    private val VAR_OPT = "P"

    override fun getName() : String { return "generate" }

    override fun getDescription() : String {
        return "Generates new files in the current project based on a subtemplate."
    }

    override protected fun doAddToParser(parser : OptionParser) : OptionParser {
        parser.accepts(SPACES_OPT, "Sets the number of spaces to use for indent in files.").withRequiredArg()
        parser.accepts(VAR_OPT, "Add a substitution variable for file filtering.").withRequiredArg()
        return parser
    }

    override protected val parameterRange = 1..1

    override protected val usage = USAGE

    override protected fun doExecute(
            cmdOptions: OptionSet,
            globalOptions : Map<*, *>,
            configuration : Configuration) : Int {
        // Make sure this is a Lazybones-created project, otherwise there are
        // no subtemplates to use.
        if (!LAZYBONES_DIR.exists()) {
            log.severe("You cannot use `generate` here: this is not a Lazybones-created project")
            return 1
        }

        try {
            val arg = TemplateArg(cmdOptions.nonOptionArguments()[0])

            val outDir = File(LAZYBONES_DIR, "${arg.templateName}-unpacked")
            outDir.mkdirs()
            unzip(templateNameToPackageFile(arg.templateName), outDir)

            val executor = InstallationScriptExecuter()
            executor.runPostInstallScriptWithArgs(
                    cmdOptions.valuesOf(VAR_OPT).fold(HashMap()) { m: MutableMap<String, String>, pair : Any? ->
                        val keyValue = pair!!.toString().split('=')
                        m[keyValue[0]] = m[keyValue[1]]!!
                        m
                    },
                    arg.qualifiers,
                    File("."),
                    outDir)

            FileUtils.deleteDirectory(outDir)

            return 0
        }
        catch (ex : PackageNotFoundException) {
            log.severe(ex.getMessage())
            return 1
        }
        catch (ex : LazybonesScriptException) {
            log.severe("Post install script caused an exception, project might be corrupt: " +
                    "${ex.getCause()?.getMessage()}")

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

    protected fun templateNameToPackageFile(name : String) : File {
        val matchingFiles = LAZYBONES_DIR.listFiles({ f : File ->
            """^${name}\-template\-.*\.zip$""".toRegex().matches(f.name)
        })

        if (matchingFiles == null || matchingFiles.isEmpty()) {
            throw PackageNotFoundException("Cannot find a subtemplate named '$name'")
        }

        return matchingFiles[0]
    }
}
