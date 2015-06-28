package uk.co.cacoethes.lazybones.commands

import wslite.http.HTTPClientException
import java.net.ConnectException
import java.net.UnknownHostException

import java.util.logging.Level
import java.util.logging.Logger

/**
 * A collection of static methods to ensure consistency between all the commands
 * when Lazybones is run offline.
 */
object OfflineMode {
    public fun isOffline(ex : Throwable?) : Boolean {
        return if (ex is HTTPClientException) isOffline(ex.getCause())
        else arrayListOf(javaClass<ConnectException>(), javaClass<UnknownHostException>()).any {
            ex != null && it.isAssignableFrom(ex.javaClass)
        }
    }

    public fun printlnOfflineMessage(t : Throwable, log : Logger, stacktrace : Boolean) {
        val ex = if (t is HTTPClientException) t.getCause() else t

        println("(Offline mode - run with -v or --stacktrace to find out why)")
        log.fine("(Error message: ${ex?.javaClass?.getSimpleName()} - ${ex?.getMessage()})")
        if (stacktrace) log.log(Level.WARNING, "", ex)
        println()
    }
}
