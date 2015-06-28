package uk.co.cacoethes.lazybones.commands

/**
 * The create and generate commands can accept qualified template names as
 * arguments. This class represents such names and allows easy access to the
 * core template name + the qualifiers.
 */
class TemplateArg(arg: String) {
    val templateName : String
    val qualifiers : List<String>

    init {
        val tmplParts = arg.split("::".toRegex())
        templateName = tmplParts.first()
        qualifiers = tmplParts.takeLast(tmplParts.size() - 1)
    }
}
