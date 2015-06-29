package uk.co.cacoethes.lazybones.commands

import uk.co.cacoethes.lazybones.config.Configuration
import java.util.*

/**
 *
 */
object Commands {
    fun getAll(config : Configuration) : List<Command> {
        return listOf(
            CreateCommand(config),
            ConfigCommand(config),
            GenerateCommand(),
            ListCommand(config),
            InfoCommand(),
            HelpCommand())
    }
}
