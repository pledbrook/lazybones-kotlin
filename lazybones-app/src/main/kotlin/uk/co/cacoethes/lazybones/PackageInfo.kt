package uk.co.cacoethes.lazybones

import uk.co.cacoethes.lazybones.packagesources.PackageSource

/**
 * Data class representing metadata about a Lazybones package.
 */
data class PackageInfo(
        val source : PackageSource,
        val name : String,
        val latestVersion : String,
        val versions : List<String>,
        val owner : String,
        val description : String = "",
        val url : String) {

    fun hasVersion() : Boolean = !versions.isEmpty()
}
