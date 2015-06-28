package uk.co.cacoethes.lazybones.config

/**
 * Created by pledbrook on 22/06/15.
 */
class ObjectConverter : Converter<Any> {
    override fun toType(value : String) : Any {
        return value
    }

    override fun toString(value : Any) : String {
        return value?.toString()
    }

    override fun validate(value : Any?) : Boolean {
        return value != null
    }
}
