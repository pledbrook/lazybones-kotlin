package uk.co.cacoethes.lazybones.config

/**
 * Created by pledbrook on 09/08/2014.
 */
class BooleanConverter : Converter<Boolean> {
    override fun toType(value : String) : Boolean {
        return value.toBoolean()
    }

    override fun toString(value : Boolean) : String {
        return value.toString()
    }

    override fun validate(value : Any?) : Boolean {
        return value is Boolean
    }
}
