package uk.co.cacoethes.lazybones.commands

import groovy.util.logging.Log
import joptsimple.OptionParser
import joptsimple.OptionSet
import uk.co.cacoethes.lazybones.config.Configuration
import uk.co.cacoethes.lazybones.packagesources.BintrayPackageSource
import wslite.http.HTTPClientException
import java.io.File
import java.util.*

import java.util.logging.Level
import java.util.regex.Pattern
import kotlin.text.Regex

/**
 * A Lazybones command that prints out all the available templates by name,
 * including any aliases that the user has configured in his or her settings.
 */
class ListCommand(val cacheDir : File) : AbstractCommand() {
    val USAGE = """\
USAGE: list
"""

    private val INDENT = "    "
    private val CACHED_OPTION = "cached"
    private val SUBTEMPLATES_OPTION = "subs"
    private val PADDING = 30

    private val VERSION_PATTERN = """\d+\.\d[^-]*(?:-SNAPSHOT)?""".toRegex()

    constructor(config : Configuration) : this(File(config.getSetting("cache.dir").toString()))

    override fun getName() : String { return "list" }

    override fun getDescription() : String {
        return "Lists the templates that are available for you to install."
    }

    override protected val parameterRange = 0..0

    override protected val usage = USAGE

    override protected fun doExecute(optionSet : OptionSet, globalOptions : Map<*, *>, config : Configuration) : Int {

        val remoteTemplates = fetchRemoteTemplates(config.getSetting("bintrayRepositories") as List<String>)

        var offline = false
        if (!optionSet.hasOptions()) {
            offline = handleRemoteTemplates(remoteTemplates, globalOptions["stacktrace"] as Boolean? ?: false)
        }

        if (optionSet.has(CACHED_OPTION) || offline) handleCachedTemplates(cacheDir)
        if (!optionSet.has(SUBTEMPLATES_OPTION)) handleMappings(config.getSubSettings("templates.mappings"))
        if (optionSet.has(SUBTEMPLATES_OPTION)) return handleSubtemplates()

        return 0
    }

    override protected fun doAddToParser(parser : OptionParser) : OptionParser {
        parser.accepts(CACHED_OPTION, "Lists the cached templates instead of the remote ones.")
        parser.accepts(SUBTEMPLATES_OPTION, "Lists any subtemplates in the current project.")
        return parser
    }

    protected fun handleRemoteTemplates(remoteTemplates : Map<String, Any>, stacktrace : Boolean) : Boolean {
        val offline = remoteTemplates.all { entry ->
            entry.value is Throwable && OfflineMode.isOffline(entry.value as Throwable)
        }

        if (offline) {
            OfflineMode.printlnOfflineMessage(
                    remoteTemplates.values().first { e -> e is Throwable } as Throwable,
                    log,
                    stacktrace)
        }
        else {
            printDetailsForRemoteTemplates(remoteTemplates, stacktrace)
        }
        return offline
    }

    protected fun handleCachedTemplates(cacheDir : File) {
        println("Cached templates")
        println()

        val templateNamePattern = """^(.*)-($VERSION_PATTERN)\.zip$""".toRegex()

        val templates = findMatchingTemplates(cacheDir, templateNamePattern)?.groupBy { f ->
            // Extract the template name as the map key
            templateNamePattern.match(f.name)?.groups?.get(1)?.value ?: ""
        }?.mapValues { entry ->
            // Extract the version numbers and make those the key value.
            entry.value.map { templateNamePattern.match(it.name)?.groups?.get(2)?.value ?: "" }
        }

        for (entry in templates) {
            println(INDENT + entry.key.padEnd(PADDING) + entry.value.sort().reverse().join(", "))
        }

        println()
    }

    protected fun printDetailsForRemoteTemplates(remoteTemplates : Map<String, Any>, stacktrace : Boolean) {
        for (repoEntry in remoteTemplates) {
            if (repoEntry.value is Exception)
                printDetailsForRemoteRepository(repoEntry.key, repoEntry.value as Exception, stacktrace)
            else
                printDetailsForRemoteRepository(repoEntry.key, repoEntry.value as List<String>, stacktrace)
        }
    }

    protected fun printDetailsForRemoteRepository(repoName : String, ex : Exception, stacktrace : Boolean) {
        println("Can't connect to ${repoName}: ${ex.getMessage()}")
        if (stacktrace) log.log(Level.WARNING, "", ex)
        println()
    }

    protected fun printDetailsForRemoteRepository(
            repoName : String,
            templateNames : Collection<String>,
            stacktrace : Boolean) {
        println("Available templates in ${repoName}")
        println()

        for (name in templateNames.sort()) {
            println(INDENT + name)
        }

        println()
    }

    protected fun fetchRemoteTemplates(bintrayRepositories : Collection<String>) : Map<String, Any> {
        return bintrayRepositories.fold(HashMap()) { m: MutableMap<String, Any>, repoName ->
            m[repoName] = fetchPackageNames(repoName)
            m
        }
    }

    protected fun fetchPackageNames(repoName : String) : Any {
        try {
            return BintrayPackageSource(repoName).listPackageNames().sort()
        }
        catch (ex : HTTPClientException) {
            return ex.getCause()!!
        }
    }

    protected fun handleMappings(mappings : Map<*, *>?) {
        if (mappings?.isNotEmpty() ?: false) {
            println("Available mappings")
            println()

            val maxKeySize = (mappings as Map<String, String>).keySet().maxBy { key -> key.length() }?.length() ?: 0

            mappings.forEach { entry ->
                println(INDENT + entry.key.padEnd(maxKeySize + 2) + "-> " + entry.value)
            }

            println()
        }
    }

    /**
     * Handle local subtemplates only
     */
    protected fun handleSubtemplates() : Int {
        // is the current dir a project created by Lazybones?
        val lazybonesDir = File(".lazybones")
        if (!lazybonesDir.exists()) {
            println("You can only use --subs in a Lazybones-created project directory")
            return 1   // Error exit code
        }

        val templateNamePattern = """^(.*)-template-($VERSION_PATTERN)\.zip$""".toRegex()
        val templates = findMatchingTemplates(lazybonesDir, templateNamePattern)

        // are there any templates available?
        if (templates != null && templates.isNotEmpty()) {
            println("Available subtemplates")
            println()

            for (f in templates) {
                val matcher = templateNamePattern.match(f.name)
                if (matcher != null) {
                    println(INDENT + matcher.groups[1]?.value?.padEnd(PADDING) + matcher.groups[2]?.value)
                }
            }

            println()
        }
        else {
            println("This project has no subtemplates")
        }

        return 0    // 'No error' exit code
    }

    private fun findMatchingTemplates(dir : File, pattern : Regex) : List<File>? {
        return dir.listFiles { f ->
            pattern.matches(f.name)
        }?.sortBy { it.name }?.toList()
    }
}
