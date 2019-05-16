package unimelb.bitbox.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Simple wrapper for using Properties(). Example:
 * <pre>
 * {@code
 * int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
 * String[] peers = Configuration.getConfigurationValue("peers").split(",");
 * }
 * </pre>
 * @author aaron
 *
 */
public class Configuration {
	private static Logger log = Logger.getLogger(Configuration.class.getName());
    // the configuration file is stored in the root of the class path as a .properties file
    private static final String CONFIGURATION_FILE = "configuration.properties";

    private static final Properties properties;

    // use static initializer to read the configuration file when the class is loaded
    static {
        properties = new Properties();
        try (InputStream inputStream = new FileInputStream(CONFIGURATION_FILE)) {
            properties.load(inputStream);
        } catch (IOException e) {
            log.warning("Could not read file " + CONFIGURATION_FILE);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
	public static Map<String, String> getConfiguration() {
        // ugly workaround to get String as generics
        Map temp = properties;
        Map<String, String> map = new HashMap<String, String>(temp);
        // prevent the returned configuration from being modified 
        return Collections.unmodifiableMap(map);
    }


    public static String getConfigurationValue(String key) {
        return properties.getProperty(key);
    }

    // private constructor to prevent initialization
    private Configuration() {
    }

}