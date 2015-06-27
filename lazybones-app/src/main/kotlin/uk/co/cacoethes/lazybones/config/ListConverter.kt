package uk.co.cacoethes.lazybones.config

/**
 * Created by pledbrook on 09/08/2014.
 */
class ListConverter : Converter<List> {
    private final Class componentType

    ListConverter(Class componentType) {
        this.componentType = componentType
    }

    override fun toType(value : String) : List {
        def converter = Converters.getConverter(componentType)
        return value?.split(/,\s+/)?.collect { converter.toType(it) }
    }

    String toString(List value) {
        return value?.join(", ")
    }

    boolean validate(Object value) {
        def converter = Converters.getConverter(componentType)
        return value == null || (value instanceof List && value.every { converter.validate(it) })
    }
}
