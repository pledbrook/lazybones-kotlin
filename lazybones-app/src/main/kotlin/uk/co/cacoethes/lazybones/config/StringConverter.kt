package uk.co.cacoethes.lazybones.config

/**
 * Created by pledbrook on 09/08/2014.
 */
class StringConverter : Converter<CharSequence> {
    override fun toType(value : String) : CharSequence {
        return value
    }

    override fun toString(value : CharSequence) : String {
        return value.toString()
    }

    override fun validate(value : Any?) : Boolean {
        return value is CharSequence
    }
}
