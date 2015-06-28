package uk.co.cacoethes.lazybones.commands

import org.apache.commons.io.FilenameUtils
import uk.co.cacoethes.lazybones.PackageInfo
import uk.co.cacoethes.lazybones.PackageNotFoundException
import uk.co.cacoethes.lazybones.packagesources.PackageSource
import uk.co.cacoethes.lazybones.util.isUrl
import java.io.File
import java.net.URI
import java.util.logging.Logger

/**
 * Builds a PackageLocation object based on the command info from the
 */
class PackageLocationBuilder(val cacheDir : File) {
    val log = Logger.getLogger(this.javaClass.getName())

    fun buildPackageLocation(
            packageName : String,
            version : String,
            packageSources : List<PackageSource>) : PackageLocation {
        if (isUrl(packageName)) {
            return buildForUrl(packageName)
        }

        return buildForBintray(packageName, version, packageSources)
    }

    private fun buildForUrl(url : String) : PackageLocation {
        val packageName = FilenameUtils.getBaseName(URI(url).getPath())

        return PackageLocation(url, cacheLocationPattern(packageName, null))
    }

    private fun buildForBintray(
            packageName : String,
            version : String,
            packageSources : List<PackageSource>) : PackageLocation {
        if (version.isNotBlank()) {
            val cacheLocation = cacheLocationPattern(packageName, version)
            val cacheFile = File(cacheLocation)
            if (cacheFile.exists()) {
                return PackageLocation(null, cacheLocation)
            }
        }

        val packageInfo = getPackageInfo(packageName, packageSources)
        val versionToDownload = version ?: packageInfo.latestVersion
        val cacheLocation = cacheLocationPattern(packageName, versionToDownload)
        val remoteLocation = packageInfo.source.getTemplateUrl(packageInfo.name, versionToDownload)

        return PackageLocation(remoteLocation, cacheLocation)
    }

    protected fun getPackageInfo(packageName : String, packageSources : List<PackageSource>) : PackageInfo {
        for (packageSource in packageSources) {
            log.fine("Searching for ${packageName} in ${packageSource}")

            val pkgInfo = packageSource.fetchPackageInfo(packageName)
            if (pkgInfo != null) {
                log.fine("Found!")
                return pkgInfo
            }
        }

        throw PackageNotFoundException(packageName)
    }

    private fun cacheLocationPattern(name : String, version : String?) : String {
        return "${cacheDir.getAbsolutePath()}/$name${if (version?.isNotBlank() ?: false) "-" + version else ""}.zip"
    }
}
