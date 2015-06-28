package uk.co.cacoethes.lazybones.commands

import groovy.transform.CompileStatic
import groovy.util.logging.Log
import uk.co.cacoethes.lazybones.PackageNotFoundException
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.util.logging.Logger

/**
 * Handles the retrieval of template packages from Bintray (or other supported
 * repositories).
 */
class PackageDownloader {
    val log = Logger.getLogger(this.javaClass.getName())

    fun downloadPackage(packageLocation : PackageLocation, packageName : String, version : String) : File {
        val packageFile = File(packageLocation.cacheLocation)

        if (!packageFile.exists()) {
            packageFile.getParentFile().mkdirs()

            // The package info may not have been requested yet. It depends on
            // whether the user specified a specific version or not. Hence we
            // try to fetch the package info first and only throw an exception
            // if it's still null.
            //
            // There is an argument for having getPackageInfo() throw the exception
            // itself. May still do that.
            log.fine("${packageLocation.cacheLocation} is not cached locally. Searching the repositories for it.")
            log.fine("Attempting to download ${packageLocation.remoteLocation} into ${packageLocation.cacheLocation}")

            try {
                val buf = ByteArray(255)
                packageFile.outputStream().use { output ->
                    URL(packageLocation.remoteLocation).openStream().use { input ->
                        var count = input.read(buf)
                        while (count != -1) {
                            output.write(buf, 0, count)
                            count = input.read(buf)
                        }
                    }
                }
            }
            catch (ex : FileNotFoundException) {
                packageFile.deleteOnExit()
                throw PackageNotFoundException(packageName, version, ex)
            }
            catch (all : Throwable) {
                packageFile.deleteOnExit()
                throw all
            }
        }

        return packageFile

    }
}
