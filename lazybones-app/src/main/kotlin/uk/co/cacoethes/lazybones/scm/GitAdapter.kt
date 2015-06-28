package uk.co.cacoethes.lazybones.scm

import groovy.util.logging.Log
import org.ini4j.Wini
import uk.co.cacoethes.lazybones.config.Configuration
import java.io.*
import java.util.logging.Logger

/**
 * An SCM adapter for git. Make sure that when executing the external processes
 * you use the {@code text} property to ensure that the process output is fully
 * read.
 */
class GitAdapter : ScmAdapter {
    val GIT = "git"
    val log = Logger.getLogger(this.javaClass.getName())

    val userName : String
    val userEmail : String

    constructor(config : Configuration) {
        // Load the current user's git config if it exists.
        val configFile = File(System.getProperty("user.home"), ".gitconfig")
        if (configFile.exists()) {
            val ini = Wini(configFile)
            val userKey = "user"
            userName = ini.get(userKey, "name")
            userEmail = ini.get(userKey, "email")
        }
        else {
            // Use Lazybones config entries if they exist.
            userName = config.getSetting("git.name")?.toString() ?: "Unknown"
            userEmail = config.getSetting("git.email")?.toString() ?: "unknown@nowhere.net"
        }
    }

    override fun getExclusionsFilename() : String {
        return ".gitignore"
    }

    /**
     * Creates a new git repository in the given location by spawning an
     * external {@code git init} command.
     */
    override fun initializeRepository(location : File) : Unit {
        execGit(arrayListOf("init"), location)
    }

    /**
     * Adds the initial files in the given location and commits them to the
     * git repository.
     * @param location The location of the git repository to commit the files
     * in.
     * @param message The commit message to use.
     */
    override fun commitInitialFiles(location : File, message : String) : Unit {
        val configCmd = "config"
        execGit(arrayListOf("add", "."), location)
        execGit(arrayListOf(configCmd, "user.name", userName), location)
        execGit(arrayListOf(configCmd, "user.email", userEmail), location)
        execGit(arrayListOf("commit", "-m", message), location)
    }

    /**
     * Executes a git command using an external process. The executable must be
     * on the path! It also logs the output of each command at FINEST level.
     * @param args The git sub-command (e.g. 'status') + its arguments
     * @param location The working directory for the command.
     * @return The return code from the process.
     */
    private fun execGit(args : List<String>, location : File) : Int {
        val process = ProcessBuilder(arrayListOf(GIT) + args).directory(location).redirectErrorStream(true).start()
        val out = StringWriter()
        val stdout = consumeProcessStream(process.getInputStream(), out)
        stdout.start()

        val exitCode = process.waitFor()
        stdout.join(1000)
        log.finest(out.toString())
        return exitCode
    }

    private fun consumeProcessStream(stream : InputStream, w : Writer) : Thread {
        val buffer = CharArray(255)
        return object : Thread() {
            init {
                setDaemon(true)
            }

            override fun run() {
                val reader = InputStreamReader(stream)
                var charsRead = 0
                while (charsRead != -1) {
                    charsRead = reader.read(buffer, 0, 256)
                    if (charsRead > 0) {
                        synchronized (w) {
                            w.write(buffer, 0, charsRead)
                        }
                    }
                }
            }
        }
    }
}
