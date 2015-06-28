package uk.co.cacoethes.lazybones.commands

import groovy.transform.CompileStatic
import groovy.util.logging.Log
import joptsimple.OptionSet
import uk.co.cacoethes.lazybones.config.Configuration

/**
 *
 */
class HelpCommand : AbstractCommand() {
    val USAGE = """\
USAGE: help <cmd>?

  where  cmd = The name of the command to show help for. If not specified,
               the command displays the generic Lazybones help.
"""

    override fun getName() : String { return "help" }

    override fun getDescription() : String {
        return "Displays general help, or help for a specific command."
    }

    override protected val parameterRange = 0..1

    override protected val usage = USAGE

    override protected fun doExecute(cmdOptions : OptionSet, globalOptions : Map<*, *>, config : Configuration) : Int {
        val cmdArgs = cmdOptions.nonOptionArguments()
        if (cmdArgs?.isNotEmpty() ?: false) {
            val cmd = Commands.getAll(config).firstOrNull { it.getName() == cmdArgs[0] }
            if (cmd != null) {
                println(cmd.getHelp(cmd.getDescription()))
            }
            else {
                log.severe("There is no command '${cmdArgs[0]}'")
                return 1
            }
        }
        else {
            showGenericHelp(config)
        }

        return 0
    }

    protected fun showGenericHelp(config : Configuration) {
        println("Lazybones is a command-line based tool for creating basic software projects from templates.")
        println("")
        println("Available commands:")
        println("")
        for (cmd in Commands.getAll(config)) {
            println("    " + cmd.getName().padEnd(15) + cmd.getDescription())
        }
        println("")
    }
}
