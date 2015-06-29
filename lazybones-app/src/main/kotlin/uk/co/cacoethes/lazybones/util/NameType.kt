package uk.co.cacoethes.lazybones.util

/**
 * Returns an identity function that simply returns a name unchanged.
 * This must be called as a method, not a property during initialisation
 * of the enum.
 */
private val identityFunction = { s: String -> s }

/**
 * Returns a special function that throws an exception if it's ever called.
 * It should only be used by the UNKNOWN type. This must be called as a
 * method, not a property during initialisation of the enum.
 */
private val unknownFunction = { s: String ->
    throw UnsupportedOperationException("Unable to convert to or from an unknown name type")
}

/**
 * Enumeration representing the various naming conventions, including the two
 * intermediate forms: camel case and lower case hyphenated.
 */
enum class NameType(intermediateType : NameType?,
                    val toIntermediateFn : (String) -> String,
                    val fromIntermediateFn : (String) -> String) {
    CAMEL_CASE(),
    HYPHENATED(),
    UNKNOWN(unknownFunction, unknownFunction)
    PROPERTY(
            CAMEL_CASE,
            { s : String -> Naming.propertyToCamelCase(s) },
            { s : String -> Naming.camelCaseToProperty(s) }),
    NATURAL(
            HYPHENATED,
            { s : String -> Naming.naturalToHyphenated(s) },
            { s : String -> Naming.hyphenatedToNatural(s) })

    val intermediateType : NameType

    init {
        this.intermediateType = intermediateType ?: this
    }

    constructor() : this(null, identityFunction, identityFunction)

    constructor(toIntermediate : (String) -> String, fromIntermediate : (String) -> String) :
            this(null, toIntermediate, fromIntermediate)

    /**
     * Uses the assigned function to convert a name string from the current
     * type to its intermediate form.
     */
    fun toIntermediate(s : String) : String {
        return toIntermediateFn.invoke(s)
    }

    /**
     * Uses the assigned function to convert a name string from an intermediate
     * form to this type.
     */
    fun fromIntermediate(s : String) : String {
        return fromIntermediateFn.invoke(s)
    }

    /**
     * Converts a name in property form into its corresponding intermediate
     * form, camel case.
     */
    fun propertyToCamelCase(content : String) : String {
        return content.capitalize()
    }

    fun camelCaseToProperty(content : String ) : String {
        val upperBound = Math.min(content.length(), 3)
        if (content.substring(0, upperBound-1).all { it.isUpperCase() }) {
            return content
        }
        else {
            return content.decapitalize()
        }
    }
}
