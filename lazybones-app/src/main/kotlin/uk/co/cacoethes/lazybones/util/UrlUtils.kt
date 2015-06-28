package uk.co.cacoethes.lazybones.util

import java.net.URI
import java.net.URISyntaxException

/**
 * Determines whether the given package name is in fact a full blown URI,
 * including scheme.
 */
fun isUrl(str : String) : Boolean {
    if (str.isEmpty()) return false
    try {
        return URI(str).getScheme() != null
    }
    catch (ignored : URISyntaxException) {
        return false
    }
}
