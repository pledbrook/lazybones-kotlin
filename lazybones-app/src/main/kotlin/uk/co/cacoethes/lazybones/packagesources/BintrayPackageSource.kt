package uk.co.cacoethes.lazybones.packagesources

import uk.co.cacoethes.lazybones.NoVersionsFoundException
import uk.co.cacoethes.lazybones.PackageInfo
import wslite.http.HTTPClientException
import wslite.rest.RESTClient
import wslite.rest.Response
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.properties.Delegates

/**
 * The default location for Lazybones packaged templates is on Bintray, which
 * also happens to have a REST API. This class uses that API to interrogate
 * the Lazybones template repository for information on what packages are
 * available and to get extra information about them.
 */
class BintrayPackageSource(val repoName : String) : PackageSource {
    val TEMPLATE_BASE_URL = "http://dl.bintray.com/v1/content/"
    val API_BASE_URL = "https://bintray.com/api/v1"
    val PACKAGE_SUFFIX = "-template"

    val restClient = RESTClient(API_BASE_URL)

    init {
        // For testing with Betamax: set up a proxy if required. groovyws-lite
        // doesn't currently support the http(s).proxyHost and http(s).proxyPort
        // system properties, so we have to manually create the proxy ourselves.
        val proxy = loadSystemProxy(true)
        if (proxy != null)  {
            restClient.getHttpClient().setProxy(proxy)
            restClient.getHttpClient().setSslTrustAllCerts(true)
        }
    }

    override fun getTemplateUrl(pkgName : String, version : String) : String {
        val pkgNameWithSuffix = pkgName + PACKAGE_SUFFIX
        return "${TEMPLATE_BASE_URL}/${repoName}/${pkgNameWithSuffix}-${version}.zip"
    }

    override fun listPackageNames() : List<String> {
        val response = restClient.get(linkedMapOf(
                "path" to "/repos/${repoName}/packages"))

        val pkgNames = (response.propertyMissing("json") as List<Map<String, Any>>).filter {
            (it["name"] as String).endsWith(PACKAGE_SUFFIX)
        }.map {
            (it["name"] as String).substringBeforeLast(PACKAGE_SUFFIX)
        }

        return pkgNames
    }

    /**
     * Fetches package information from Bintray for the given package name or
     * {@code null} if there is no such package. This may also throw instances
     * of {@code wslite.http.HTTPClientException} if there are any problems
     * connecting to the Bintray API.
     * @param pkgName The name of the package for which you want the information.
     * @return The required package info or {@code null} if the repository
     * doesn't host the requested packaged.
     */
    override fun fetchPackageInfo(pkgName : String) : PackageInfo? {
        val pkgNameWithSuffix = pkgName + PACKAGE_SUFFIX

        val response : Response
        try {
            response = restClient.get(linkedMapOf("path" to "/packages/${repoName}/${pkgNameWithSuffix}"))
        }
        catch (ex : HTTPClientException) {
            if (ex.getResponse()?.getStatusCode() != 404) {
                throw ex
            }

            return null
        }

        // The package may have no published versions, so we need to handle the
        // case where `latest_version` is null.
        val data = response.propertyMissing("json") as Map<String, Any?>
        if (data["latest_version"] == null) {
            throw NoVersionsFoundException(pkgName)
        }

        val pkgInfo = PackageInfo(
                source = this,
                name = (data["name"] as String).substringBeforeLast(PACKAGE_SUFFIX),
                latestVersion = data["latest_version"] as String,
                versions = data["versions"] as List<String>,
                owner = data["owner"] as String,
                description = if (data["desc"] != null) data["desc"] as String else "",
                url = data["desc_url"] as String)

        return pkgInfo
    }

    /**
     * Reads the proxy information from the {@code http(s).proxyHost} and {@code http(s).proxyPort}
     * system properties if set and returns a {@code java.net.Proxy} instance configured with
     * those settings. If the {@code proxyHost} setting has no value, then this method returns
     * {@code null}.
     * @param useHttpsProxy {@code true} if you want the HTTPS proxy, otherwise {@code false}.
     */
    private fun loadSystemProxy(useHttpsProxy : Boolean) : Proxy? {
        val propertyPrefix = if (useHttpsProxy) "https" else "http"
        val proxyHost = System.getProperty("${propertyPrefix}.proxyHost")
        if (proxyHost == null || proxyHost.isBlank()) return null

        val proxyPort = System.getProperty("${propertyPrefix}.proxyPort")?.toInt() ?:
                if (useHttpsProxy) 443 else 80

        return Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
    }
}
