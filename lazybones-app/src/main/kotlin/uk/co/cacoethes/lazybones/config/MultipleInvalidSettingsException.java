package uk.co.cacoethes.lazybones.config;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a problem with the value of a Lazybones configuration setting.
 * For example, this is often thrown when the value type does not match the
 * configured type of the setting.
 */
public class MultipleInvalidSettingsException extends RuntimeException {
    public MultipleInvalidSettingsException(List<String> settingNames) {
        this(settingNames, getDefaultMessage(settingNames));
    }

    public MultipleInvalidSettingsException(List<String> settingNames, String message) {
        super(message);
        this.settingNames = new ArrayList(settingNames);
    }

    public List<String> getSettingNames() {
        return this.settingNames;
    }

    private static String getDefaultMessage(final List<String> settingNames) {
        return "The following configuration settings are invalid: " + DefaultGroovyMethods.join(settingNames, ", ");
    }

    private final List<String> settingNames;
}
