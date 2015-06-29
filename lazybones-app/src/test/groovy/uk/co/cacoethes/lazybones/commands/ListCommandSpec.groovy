package uk.co.cacoethes.lazybones.commands

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Created by tbarker on 12/18/13.
 */
class ListCommandSpec extends Specification {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder()

    void "mappings are printed IF mappings exist"() {
        given:
        def originalOut = System.out
        def stream = new ByteArrayOutputStream()
        System.out = new PrintStream(stream)
        def empty = [:]
        def mappings = [
                customRatpack: "http://dl.dropboxusercontent.com/u/29802534/custom-ratpack.zip",
                doesNotExist: "file:///does/not/exist"
        ]
        def listCmd = new ListCommand(tmpDir.root)

        when:
        listCmd.handleMappings(empty)

        then:
        0 == stream.size()

        when:
        listCmd.handleMappings(mappings)

        then:
        def output = stream.toString()
        output.startsWith("Available mappings")
        output =~ /\s+customRatpack  -> http:\/\/dl.dropboxusercontent.com\/u\/29802534\/custom-ratpack.zip\s+/
        output =~ /\s+doesNotExist   -> file:\/\/\/does\/not\/exist\s+/

        cleanup:
        System.out = originalOut
    }
}
