package uk.co.cacoethes.lazybones.config

import java.net.URL

/**
 * We use URL for the type here because JsonBuilder stringifies the
 * type as you would expect. It treats URI as an object, and so stringifies
 * the individual properties.
 */
class UriConverter : Converter<URL> {
    override fun toType(value : String) : URL {
        return URL(value)
    }

    override fun toString(value : URL) : String {
        return value.toString()
    }

    override fun validate(value : Any?) : Boolean {
        return value is URL
    }
}
