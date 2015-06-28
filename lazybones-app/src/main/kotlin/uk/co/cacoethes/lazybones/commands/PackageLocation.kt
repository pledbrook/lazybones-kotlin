package uk.co.cacoethes.lazybones.commands

/**
 * Data class representing the location of a template package in a remote
 * repository and its corresponding location in the Lazybones cache.
 */
data class PackageLocation(val remoteLocation : String?, val cacheLocation : String)
