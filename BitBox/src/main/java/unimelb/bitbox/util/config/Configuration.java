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
import java.util.concurrent.atomic.AtomicLong;
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
	static Logger log = Logger.getLogger(Configuration.class.getName());
    // the configuration file is stored in the root of the class path as a .properties file
    private static final String CONFIGURATION_FILE = "configuration.properties";
    private static final File file = new File(CONFIGURATION_FILE);
    private static final FileWatcher watcher = new FileWatcher(file, Configuration::updateValues, 100);
    private static AtomicLong modified = new AtomicLong();

    static final List<CfgValue> watchedValues = Collections.synchronizedList(new ArrayList<>());

    static void updateValues() {
        long nextModified = file.lastModified();
        log.info(modified.get() + " // " + nextModified);
        if (modified.getAndSet(nextModified) != nextModified) {
            log.info("Configuration file modified");
            synchronized (watchedValues) {
                loadProperties();
                watchedValues.forEach(CfgValue::get);
            }
        }
    }

    private static Properties properties;
    private static Properties getProperties() {
        if (properties == null) {
            updateValues();
        }
        return properties;
    }

    // use static initializer to read the configuration file when the class is loaded
    static {
        updateValues();
        watcher.start();
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream inputStream = new FileInputStream(CONFIGURATION_FILE)) {
            properties.load(inputStream);
        } catch (IOException e) {
            log.warning("Could not read file " + CONFIGURATION_FILE);
        }
    }

    public static boolean contains(String key) {
        return getProperties().containsKey(key);
    }

    public static String getConfigurationValue(String key) {
        return getProperties().getProperty(key).trim();
    }

    // private constructor to prevent initialization
    private Configuration() {
    }
}