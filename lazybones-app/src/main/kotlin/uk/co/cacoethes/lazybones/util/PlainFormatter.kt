package uk.co.cacoethes.lazybones.util

import java.io.PrintWriter
import java.io.StringWriter
import java.util.logging.Formatter
import java.util.logging.LogRecord

/**
 * This is a java.util.logging formatter that simply prints the log messages
 * as-is, without any decoration. It basically turns log statements into
 * simple {@code println()}s. But, you have the advantage of log levels!
 */
class PlainFormatter : Formatter() {
    override fun format(record : LogRecord) : String {
        val sw = StringWriter()
        sw append record.getMessage() + '\n'

        //copied from SimpleFormatter
        if (record.getThrown() != null) {
            record.getThrown().printStackTrace(PrintWriter(sw))
        }

        return sw.toString()
    }
}
