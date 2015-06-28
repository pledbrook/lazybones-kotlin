package uk.co.cacoethes.lazybones.commands

import groovy.transform.CompileStatic
import groovy.util.logging.Log
import joptsimple.OptionException
import joptsimple.OptionParser
import joptsimple.OptionSet
import uk.co.cacoethes.lazybones.config.Configuration
import uk.co.cacoethes.lazybones.Options
import java.io.StringWriter
import java.util.logging.Logger

/**
 * Base class for most command implementations. It mostly provides help with
 * parsing extra command-specific options.
 */
abstract class AbstractCommand : Command {
    val log = Logger.getLogger(this.javaClass.getName())

    override fun execute(args : List<String>, globalOptions : Map<*, *>, config : Configuration) : Int {
        val cmdOptions = parseArguments(args, parameterRange)
        if (cmdOptions == null) return 1

        if (cmdOptions.has(Options.HELP_SHORT)) {
            println(getHelp(getDescription()))
            return 0
        }

        return doExecute(cmdOptions, globalOptions, config)
    }

    protected abstract fun doExecute(optionSet : OptionSet, globalOptions : Map<*, *>, config : Configuration) : Int

    /**
     * Returns the number of arguments this command can accept, on top of the
     * default ones handled by this class, such as {@code -h/--help}.
     */
    protected abstract val parameterRange : IntRange

    /**
     * Returns the USAGE string for this command.
     */
    protected abstract val usage : String

    /**
     * Creates a JOptSimple parser. This should return a parser that is already
     * configured with the options supported by the command. By default this
     * returns an empty parser without any defined options.
     */
    protected fun createParser() : OptionParser {
        val parser = OptionParser()
        parser.acceptsAll(arrayListOf(Options.HELP_SHORT, Options.HELP),  "Displays usage.")

        return doAddToParser(parser)
    }

    open protected fun doAddToParser(parser : OptionParser) : OptionParser { return parser }

    /**
     * Uses the parser from {@link AbstractCommand#createParser()} to parse the
     * part of the command line specific to this command and returns a JOptSimple
     * set of parsed options.
     * @param args The command line arguments given to this command.
     * @param validArgCount A range specifying how many non-option arguments
     * (those that don't begin with '-' or '--') can be provided to the command.
     * If the number of non-option arguments falls outside this range, the method
     * returns null and prints an error to the console.
     * @return The option set, or {@code null} if the arguments can't be parsed
     * for whatever reason
     *
     * TODO This should probably throw exceptions in the case of errors. Too much
     * information is lost with a {@code null} return.
     */
    protected fun parseArguments(args : List<String>, validArgCount : IntRange) : OptionSet? {
        try {
            val options = createParser().parse(*args.toTypedArray())

            if (!(options.nonOptionArguments().size() in validArgCount) && !options.has(Options.HELP_SHORT)) {
                log.severe(getHelp("Incorrect number of arguments."))
                return null
            }

            return options
        }
        catch (ex : OptionException) {
            log.severe(getHelp(ex.getMessage() ?: "<unknown>"))
            return null
        }
    }

    /**
     * Returns a help string to display for usage. It incorporates the given
     * message, the command's usage string, and the supported JOptSimple options.
     */
    override fun getHelp(message : String) : String {
        val writer = StringWriter()
        createParser().printHelpOn(writer)

        return """\
${message}

${usage}
${writer}"""
    }
}
