package uk.co.cacoethes.lazybones.util

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileOutputStream

/**
 * This class contains some static utility methods defined in such a way that
 * they can be used as Groovy extension methods. The zip-related methods have
 * been shamelessly borrowed from Tim Yate's Groovy Common Extensions library
 * but modified to support retention of file permissions.
 */

val EXCEPTION_TEXT = "File#unzip() has to be called on a *.zip file."

/**
 * Unzips a file to a target directory, retaining the file permissions where
 * possible. You can also provide a closure that acts as a filter, returning
 * {@code true} if you want the file or directory extracted, {@code false}
 * otherwise.
 * @param self The zip file to extract.
 * @param destination The directory to extract the zip to. Of course, it must
 * be a directory, otherwise this method throws an IllegalArgumentException.
 * @param filter (optional) A closure that acts as a filter. It must accept a
 * single argument that is a File and return {@code true} if that zip entry
 * should be extracted, or {@code false} otherwise.
 */
public fun unzip(self: File, destination: File, filter: ((File) -> Boolean)?  = null) : Collection<File>{
    checkUnzipFileType(self)
    checkUnzipDestination(destination)

    val zipFile = ZipFile(self)

    try {
        return unpackZipEntries(zipFile, destination, filter)
    }
    finally {
        zipFile.close()
    }
}

fun unpackZipEntries(zipFile : ZipFile, destination : File, filter : ((File) -> Boolean)?) : Collection<File> {
    val unzippedFiles : MutableList<File> = arrayListOf()

    for (entry in zipFile.getEntries()) {
        val file = File(destination, entry.getName())
        if (filter == null || filter.invoke(file)) {
            if (entry.isDirectory()) {
                file.mkdirs()
            }
            else {
                file.getParentFile()?.mkdirs()
                val buf = ByteArray(255)

                FileOutputStream(file).use { output ->
                    zipFile.getInputStream(entry).use { input ->
                        var count = input.read(buf)
                        while (count != -1) {
                            output.write(buf, 0, count)
                            count = input.read(buf)
                        }
                    }
                }
            }

            unzippedFiles.add(file)
            updateFilePermissions(file, entry.getUnixMode())
        }
    }

    return unzippedFiles
}

/**
 * <p>Sets appropriate Unix file permissions on a file based on a 'mode'
 * number, such as 0644 or 0755. Note that those numbers are in octal
 * format!</p>
 * <p>The left-most number represents the owner permission (1 = execute,
 * 2 = write, 4 = read, 5 = read/exec, 6 = read/write, 7 = read/write/exec).
 * The middle number represents the group permissions and the last number
 * applies to everyone. In reality, because of limitations in the underlying
 * Java API this method will only honour owner and everyone settings. The
 * group permissions will be set to the same as those for everyone.</p>
 */
public fun updateFilePermissions(self : File, unixMode : Int) : Unit {
    self.setExecutable((unixMode and 0x40) != 0, unixMode and 0x01 == 0)
    self.setReadable((unixMode and 0x100) != 0, unixMode and 0x04 == 0)
    self.setWritable((unixMode and 0x80) != 0, unixMode and 0x02 == 0)
}

/**
 * Checks that the given file is both a file (not a directory, link, etc)
 * and that its name has a .zip extension.
 */
private fun checkUnzipFileType(self : File) {
    if (!self.isFile()) throw IllegalArgumentException(EXCEPTION_TEXT)

    if (!self.getName().toLowerCase().endsWith(".zip")) throw IllegalArgumentException(EXCEPTION_TEXT)
}

/**
 * Checks that the given file is a directory.
 */
private fun checkUnzipDestination(file : File) {
    if (!file.isDirectory()) throw IllegalArgumentException("'destination' has to be a directory.")
}
