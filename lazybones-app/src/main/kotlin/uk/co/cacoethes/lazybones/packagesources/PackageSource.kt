package uk.co.cacoethes.lazybones.packagesources

import uk.co.cacoethes.lazybones.PackageInfo

/**
 * Represents a source of information about Lazybones packaged templates. This
 * could be a REST service, a cached file, or something else.
 */
interface PackageSource {
    /**
     * Returns a list of the available packages. If there are no packages, this
     * returns an empty list.
     */
    fun listPackageNames() : List<String>

    /**
     * Returns details about a given package. If no package is found with the
     * given name, this returns {@code null}.
     */
    fun fetchPackageInfo(packageName : String) : PackageInfo?

    /**
     * Returns the URL to download particular package and version from this package source
     */
    fun getTemplateUrl(pkgName : String, version : String) : String
}
