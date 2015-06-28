package uk.co.cacoethes.lazybones.packagesources

import uk.co.cacoethes.lazybones.config.Configuration

/**
 * Factory class for generating package sources, i.e. repositories that provide
 * Lazybones template packages.
 */
class PackageSourceBuilder {
    /**
     * Builds an ordered list of package sources which could provide the given package name.
     *
     * @param packageName
     * @param config
     * @return
     */
    fun buildPackageSourceList(config : Configuration) : List<PackageSource> {
        val repositoryList = config.getSetting("bintrayRepositories") as List<String>
        return repositoryList.map { BintrayPackageSource(it) }
    }
}
