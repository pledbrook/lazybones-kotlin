package uk.co.cacoethes.lazybones

import joptsimple.OptionParser
import uk.co.cacoethes.lazybones.Options.*

/**
 * Simple static factory method class for creating a JOptSimple parser for the
 * Lazybones command line application.
 */
//@SuppressWarnings("NoWildcardImports")
object OptionParserBuilder {

    fun makeOptionParser() : OptionParser {
        // These are the global options available for all commands.
        val parser = OptionParser()
        parser.accepts(STACKTRACE, "Show stack traces when exceptions are thrown.")
        parser.acceptsAll(arrayListOf(VERBOSE, VERBOSE_SHORT), "Display extra information when running commands.")
        parser.accepts(QUIET, "Show minimal output.")
        parser.accepts(INFO, "Show normal amount of output (default).")
        parser.accepts(LOG_LEVEL, "Set logging level, e.g. OFF, SEVERE, INFO, FINE, etc.").withRequiredArg()
        parser.accepts(VERSION, "Print out the Lazybones version and then end.")
        parser.acceptsAll(arrayListOf(HELP, HELP_SHORT), "Print out the Lazybones help and then end.")

        // Ensures that only options up to the sub-command ('create, 'list',
        // etc.) are parsed.
        parser.posixlyCorrect(true)
        return parser
    }
}
