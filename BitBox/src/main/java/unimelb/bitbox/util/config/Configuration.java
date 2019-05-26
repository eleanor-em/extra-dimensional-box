package unimelb.bitbox.util.config;

import unimelb.bitbox.util.fs.FileWatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private static final File file = new File(CONFIGURATION_FILE);
    private static final FileWatcher watcher = new FileWatcher(file, Configuration::updateValues);
    private static long modified;

    static final List<CfgValue> watchedValues = Collections.synchronizedList(new ArrayList<>());

    static boolean isOutdated() {
        return new File(CONFIGURATION_FILE).lastModified() != modified;
    }

    static void updateValues() {
        if (isOutdated()) {
            synchronized (watchedValues) {
                loadProperties();
                watchedValues.forEach(CfgValue::get);
            }
        }
    }

    private static Properties properties;

    // use static initializer to read the configuration file when the class is loaded
    static {
        loadProperties();
        watcher.start();
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream inputStream = new FileInputStream(CONFIGURATION_FILE)) {
            modified = new File(CONFIGURATION_FILE).lastModified();
            properties.load(inputStream);
        } catch (IOException e) {
            log.warning("Could not read file " + CONFIGURATION_FILE);
        }
    }

    public static boolean contains(String key) {
        return properties.containsKey(key);
    }

    public static String getConfigurationValue(String key) {
        if (properties == null) {
            loadProperties();
        }
        // EXTENSION: prevent spurious errors due to typos
        return properties.getProperty(key).trim();
    }

    // private constructor to prevent initialization
    private Configuration() {
    }
}