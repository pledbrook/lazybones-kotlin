package uk.co.cacoethes.lazybones.util

/**
 * <p>Provides static methods for converting between different forms of names,
 * such as camel case, hyphenated lower case, and natural. You can find the
 * rules for the conversion in the {@linkplain uk.co.cacoethes.gradle.util.NameConverterSpec
 * unit specification} for this class.</p>
 * <p><em>Note</em> Names that don't conform to the expected formats may lead to
 * unexpected behaviour. Basically the conversions are undefined. Also, sequences
 * of numbers are treated as words, so Sample245Book becomes sample-245-book.</p>
 * <p>The conversion itself always goes through intermediate forms: either
 * camel case, lower case hyphenated, or both. This reduces the amount of code
 * needed to handle multiple types.</p>
 * @author Peter Ledbrook
 */
object Naming {

    /**
     * Converts a string from one naming convention to another.
     * @param args A set of named arguments. Both {@code from} and {@code to}
     * are required and should be the corresponding {@link NameType}
     * @param content The string to convert. If this is either {@code null} or
     * an empty string, then the method returns that value.
     * @return A new string representing {@code content} in the requested
     * form.
     */
    public fun convert(from : NameType, to : NameType, content : String) : String {
        return NameWithType(NameType.UNKNOWN, content).from(from).to(to)
    }

    /**
     * Starts a conversion of a string from one naming convention to another.
     * It's used like this:
     * <pre>
     * convert("test-string").from(NameType.CAMEL_CASE).to(NameType.HYPHENATED)
     * </pre>
     * Both the {@code from()} and the {@code to()} are required for the
     * conversion to actuall take place.
     * @param content The string to convert. If this is either {@code null} or
     * an empty string, then that value is what is ultimately returned.
     * @return An object that you can call {@code from()} on to specify the
     * current form of the given name.
     */
    fun convert(content : String) : NameWithType {
        return NameWithType(NameType.UNKNOWN, content)
    }

    /**
     * Converts a name in camel case (such as HelloWorld) to the hyphenated
     * equivalent (hello-world).
     */
    fun camelCaseToHyphenated(name : String) : String {
        if (name.isBlank()) return name

        val out = StringBuilder(name.length() + 5)
        val lexer = BasicLexer(name)

        out append lexer.nextWord()
        for (part in lexer) {
            out append '-'
            out append part
        }
        return out.toString()
    }

    /**
     * Converts a hyphenated name (such as hello-world) to the camel-case
     * equivalent (HelloWorld).
     */
    fun hyphenatedToCamelCase(name : String) : String {
        if (name.isBlank()) return name

        val out = StringBuilder()
        "-([a-zA-Z0-9])".toRegex().replace(name) { m ->
            m.groups[0]?.value?.capitalize() ?: ""
        }

        out.replace(0, 1, name[0].toUpperCase().toString())
        return out.toString()
    }

    /**
     * Converts a name in hyphenated form to its natural form. Hyphenated is
     * the intermediate form for natural.
     */
    fun hyphenatedToNatural(content : String) : String {
        return content.split('-').map {
            return it.capitalize()
        }.join(" ")
    }

    /**
     * Converts a name in natural form into its corresponding intermediate
     * form, hyphenated.
     */
    fun naturalToHyphenated(content : String) : String {
        return content.split(' ').map {
            if (it.length() > 1 && it[1].isUpperCase()) return it
            else return it.toLowerCase()
        }.join("-")
    }

    /**
     * Converts a name in property form into its corresponding intermediate
     * form, camel case.
     */
    fun propertyToCamelCase(content : String) : String {
        return content.capitalize()
    }

    /**
     * Converts a name in camel case form into its property form. Camel case is
     * the intermediate form for property names.
     */
    fun camelCaseToProperty(content : String ) : String {
        val upperBound = Math.min(content.length(), 3)
        if (content.substring(0, upperBound-1).all { it.isUpperCase() }) {
            return content
        }
        else {
            return content.decapitalize()
        }
    }

    /**
     * Stores a name string along with the current form of that name. There is
     * no verification that the given name string actually conforms to the given
     * type.
     */
    private class NameWithType(val type : NameType, val content : String) {
        /**
         * Effectively assigns a name type to the current name string and
         * converts that string to its corresponding intermediate type. There
         * is no verification that name string is actually of the given form.
         * @param type The form to convert from.
         * @return A new {@code NameType} object with the same name string
         * as this one, but with the assigned type.
         */
        fun from(type : NameType) : NameWithType {
            return NameWithType(type.intermediateType ?: type, type.toIntermediate(content))
        }

        /**
         * Performs the conversion from an intermediate type to the target
         * type. If the current type isn't one of the intermediate types, this
         * method will fail to work properly. It won't throw any exceptions
         * though, the result will just be incorrect.
         * @param type The name type to convert the current name string to.
         * @return The converted name string.
         */
        fun to(type : NameType) : String {
            // If the from and to types have different intermediate types, we
            // first need to convert between the two intermediate types.
            var currentContent = this.content
            if (this.type.intermediateType == NameType.CAMEL_CASE && type.intermediateType == NameType.HYPHENATED) {
                currentContent = camelCaseToHyphenated(currentContent)
            }
            else if (this.type.intermediateType == NameType.HYPHENATED &&
                    type.intermediateType == NameType.CAMEL_CASE) {
                currentContent = hyphenatedToCamelCase(currentContent)
            }

            return type.fromIntermediate(currentContent)
        }
    }

    private class BasicLexer(val source : String) {
        val UPPER = 0
        val LOWER = 1
        val OTHER = 2

        var position : Int = 0

        fun iterator() : Iterator<String> {
            return object : Iterator<String> {
                init {
                    if (position != 0) throw IllegalStateException("This lexer has already been iterated over")
                }

                var currentWord : String? = null

                override fun next(): String {
                    return currentWord!!
                }

                override fun hasNext(): Boolean {
                    currentWord = nextWord()
                    return currentWord != null
                }
            }
        }

        fun nextWord() : String? {
            val maxPos = source.length()
            if (position == maxPos) return null

            val ch = source[position]
            var state = getType(ch)

            // Start looking at the next characters
            var pos = position + 1
            while (pos < maxPos) {
                // When this character is different to the one before,
                // it is a word boundary unless this is a lower-case
                // letter and the previous one was upper-case.
                val newState = getType(source[pos])
                if (newState != state && (state != UPPER || newState != LOWER)) break

                // Look ahead if both the previous character and the current
                // one are upper case. If, and only if, the next character is
                // lower case, this character is treated as a word boundary.
                if (state == UPPER && newState == UPPER &&
                        (pos + 1) < maxPos && getType(source[pos + 1]) == LOWER) break

                // Go to next character
                state = newState
                pos++
            }

            var word = source.substring(position, pos)
            position = pos

            // Do we need to lower case this word?
            if (ch.isUpperCase() && (word.length() == 1 || word[1].isLowerCase())) {
                word = word.toLowerCase()
            }

            return word
        }

        private fun getType(ch : Char) : Int {
            return if (ch.isUpperCase()) UPPER else (if (ch.isLowerCase()) LOWER else OTHER)
        }
    }
}
