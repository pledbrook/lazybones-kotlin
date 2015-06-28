package uk.co.cacoethes.lazybones.commands

import java.io.File

/**
 * Represents all the input data for the Lazybones create command, such as
 * template name, version, etc.
 */
data class CreateCommandInfo(
    val packageArg : TemplateArg,
    val requestedVersion : String,
    val targetDir : File)
