package uk.co.cacoethes.util

import spock.lang.Specification
import uk.co.cacoethes.lazybones.util.UtilPackage

/**
 * Created by tbarker on 12/18/13.
 */
class UrlUtilsSpec extends Specification {

    void "test url validation"() {
        expect:
        a == UtilPackage.isUrl(b)

        where:
        a     | b
        true  | "http://foo.com"
        false | "foo.com"
        false | ""
    }
}
