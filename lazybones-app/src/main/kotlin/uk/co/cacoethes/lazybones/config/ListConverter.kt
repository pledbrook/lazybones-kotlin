package uk.co.cacoethes.lazybones.config

/**
 * Created by pledbrook on 09/08/2014.
 */
class ListConverter<E>(val componentType : Class<E>) : Converter<List<E>> {

    override fun toType(value : String) : List<E> {
        val converter = Converters.getConverter(componentType) ?: throw NoConverterFoundException(componentType)
        return value.split(""",\s+""".toRegex()).map { converter.toType(it) as E }
    }

    override fun toString(value : List<E>) : String {
        return value.map { it?.toString() ?: "" }.join(", ")
    }

    override fun validate(value : Any?) : Boolean {
        val converter = Converters.getConverter(componentType) ?: throw NoConverterFoundException(componentType)
        return (value is List<*> && value.all { converter.validate(it) })
    }
}
