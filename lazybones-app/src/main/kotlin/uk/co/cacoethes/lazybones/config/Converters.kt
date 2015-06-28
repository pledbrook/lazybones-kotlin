package uk.co.cacoethes.lazybones.config

import java.net.URI

/**
 * Created by pledbrook on 09/08/2014.
 */
object Converters {
    val CONVERTER_MAP : Map<Class<out Any>, Converter<out Any>> = mapOf(
            javaClass<Any>() to ObjectConverter(),
            javaClass<Boolean>() to BooleanConverter(),
            javaClass<Int>() to IntegerConverter(),
            javaClass<String>() to StringConverter(),
            javaClass<URI>() to UriConverter())

    fun getConverter<T>(theClass : Class<T> ) : Converter<out Any>? {
        if (theClass.isArray()) return ListConverter(theClass.getComponentType())

        return CONVERTER_MAP.get(theClass)
    }
}
