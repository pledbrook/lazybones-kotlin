package uk.co.cacoethes.lazybones.config;

/**
 * Created by pledbrook on 09/08/2014.
 */
interface Converter<T> {
    fun toType(value : String) : T
    fun toString(value : T) : String
    fun validate(value : Any?) : Boolean
}
