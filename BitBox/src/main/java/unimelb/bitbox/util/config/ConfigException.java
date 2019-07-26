package unimelb.bitbox.util.config;

/**
 * Encapsulates exceptions that may occur while loading the configuration file.
 *
 * @author Eleanor McMurtry
 */
public class ConfigException extends Exception {
    private ConfigException(Exception cause) {
        super(cause);
    }
    private ConfigException(String reason) {
        super(reason);
    }

    /**
     * An exception that occurred because a key was missing from the config file.
     */
    static ConfigException keyMissing(String key) {
        return new ConfigException("Config entry \"" + key + "\" not found");
    }

    /**
     * An exception that occurred because the config file was missing.
     */
    static ConfigException fileMissing() {
        return new ConfigException("Config file not found");
    }

    /**
     * Wraps any kind of exception within this type.
     */
    static ConfigException via(Exception cause) {
        return new ConfigException(cause);
    }

    /**
     * An exception that occurred because a key was present, but was not formatted correctly.
     */
    static ConfigException formatError(String key, String message) {
        return new ConfigException("Config entry \"" + key + "\" formatted incorrectly: " + message);
    }
}
