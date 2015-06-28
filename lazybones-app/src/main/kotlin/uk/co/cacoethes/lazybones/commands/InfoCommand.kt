package uk.co.cacoethes.lazybones.commands

import groovy.transform.CompileStatic
import groovy.util.logging.Log
import joptsimple.OptionSet
import uk.co.cacoethes.lazybones.config.Configuration
import uk.co.cacoethes.lazybones.packagesources.BintrayPackageSource
import uk.co.cacoethes.lazybones.NoVersionsFoundException
import uk.co.cacoethes.lazybones.PackageInfo
import wslite.http.HTTPClientException

import java.util.logging.Level

/**
 *
 */
class InfoCommand : AbstractCommand() {
    val USAGE = """\
USAGE: info <template>

  where  template = The name of the project template you want information
                    about
"""

    override fun getName() : String { return "info" }

    override fun getDescription() : String {
        return "Displays information about a template, such as latest version, description, etc."
    }

    override protected val usage = USAGE

    override protected val parameterRange = 1..1

    override fun doExecute(cmdOptions : OptionSet, globalOptions : Map<*, *>, config : Configuration ) : Int {
        val packageName = cmdOptions.nonOptionArguments()[0]

        log.info("Fetching package information for '${packageName}' from Bintray")

        // grab the package from the first repository that has it
        val pkgInfo : PackageInfo?
        try {
            pkgInfo = findPackageInBintrayRepositories(
                    packageName,
                    config.getSetting("bintrayRepositories") as List<String>)
        }
        catch (ex : NoVersionsFoundException) {
            log.severe("No version of '${packageName}' has been published")
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
            println("Cannot fetch package info")
            return 1
        }
        catch (all : Throwable) {
            log.severe("Unexpected failure: ${all.getMessage()}")
            if (globalOptions["stacktrace"] as Boolean? ?: false) log.log(Level.SEVERE, "", all)
            return 1
        }

        if (pkgInfo == null) {
            println("Cannot find a template named '${packageName}'")
            return 1
        }

        println("Name:        " + pkgInfo.name)
        println("Latest:      " + pkgInfo.latestVersion)
        if (pkgInfo.description.isNotBlank()) println("Description: " + pkgInfo.description)
        if (pkgInfo.owner.isNotBlank()) println("Owner:       " + pkgInfo.owner)
        println("Versions:    " + pkgInfo.versions.join(", "))

        if (pkgInfo.url.isNotBlank()) {
            println()
            println("More information at " + pkgInfo.url)
        }
        return 0
    }

    protected fun findPackageInBintrayRepositories(pkgName: String, repos: Collection<String>) : PackageInfo? {
        for (bintrayRepoName in repos) {
            val pkgInfo = BintrayPackageSource(bintrayRepoName).fetchPackageInfo(pkgName)
            if (pkgInfo != null) return pkgInfo
        }

        return null
    }
}
