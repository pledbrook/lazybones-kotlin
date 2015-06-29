package uk.co.cacoethes.lazybones.commands;

import uk.co.cacoethes.lazybones.config.Configuration;

/**
 * An implementation of a sub-command for Lazybones.
 */
interface Command {
    /**
     * The name of the command as given on the command line. It should be all
     * lowercase with hyphens between words.
     */
    fun getName() : String

    /**
     * A single line description of the command.
     */
    fun getDescription() : String

    /**
     * A block of text giving help/usage information for the command. This
     * block of text should incorporate the given message.
     */
    fun getHelp(message : String) : String

    /**
     * Executes the command!
     * @param args A list of non-option arguments that were passed on the
     * command line.
     * @param globalOptions The global Lazybones options, such as {@code logLevel}.
     * @param config A reference to the Lazybones configuration (from config.groovy).
     * @return An exit code. A non-zero value indicates an error.
     */
    fun execute(args : List<String>, globalOptions : Map<String, Any>, config : Configuration) : Int
}
