package uk.co.cacoethes.lazybones.config

/**
 * Created by pledbrook on 09/08/2014.
 */
class IntegerConverter : Converter<Int> {
    override fun toType(value : String) : Int {
        return value.toInt()
    }

    override fun toString(value : Int) : String {
        return value.toString()
    }

    override fun validate(value : Any?) : Boolean {
        return value is Int
    }
}
