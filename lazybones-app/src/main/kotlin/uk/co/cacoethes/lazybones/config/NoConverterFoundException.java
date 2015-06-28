package uk.co.cacoethes.lazybones.config;

/**
 * Represents a problem with the value of a Lazybones configuration setting.
 * For example, this is often thrown when the value type does not match the
 * configured type of the setting.
 */
public class NoConverterFoundException extends RuntimeException {
    private Class requestedClass;

    public NoConverterFoundException(final Class requestedClass) {
        this(requestedClass, getDefaultMessage(requestedClass));
    }

    public NoConverterFoundException(final Class requestedClass, final String message) {
        super(message);
        this.requestedClass = requestedClass;
    }

    public Class getRequestedClass() { return this.requestedClass; }

    private static String getDefaultMessage(final Class requestedClass) {
        return "No converter could be found for values of type " + requestedClass.getName();
    }
}
