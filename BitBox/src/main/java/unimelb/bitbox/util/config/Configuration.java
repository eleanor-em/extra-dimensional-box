package unimelb.bitbox.util.config;

import functional.algebraic.Result;
import unimelb.bitbox.util.network.Conversion;
import unimelb.bitbox.util.network.HostPort;
import unimelb.bitbox.util.network.HostPortParseException;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Simple wrapper for using Properties().
 *
 * @author Aaron Harwood
 * @author Eleanor McMurtry
 */
public class Configuration {
    private static String mode;
    private static String path;
    private static int port;
    private static int clientPort;
    private static String advertisedName;
    private static int maximumConnections;
    private static int blockSize;
    private static int syncInterval;
    private static List<HostPort> peers;

    private static boolean initialised = false;

    private static Result<Properties, IOException> loadProperties(File file) {
        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream(file)) {
            return Result.of(() -> {
                properties.load(inputStream);
                return properties;
            });
        } catch (IOException e) {
            return Result.error(e);
        }
    }


    public static void load(String filename) throws ConfigException {
        try {
            var properties = loadProperties(new File(filename)).get();

            mode = getOrThrow(properties, "mode");
            path = getOrThrow(properties, "path");
            port = getIntOrThrow(properties, "port");
            clientPort = getIntOrThrow(properties, "clientPort");
            advertisedName = getOrThrow(properties, "advertisedName");
            maximumConnections = getIntOrThrow(properties, "maximumConnections");
            blockSize = getIntOrThrow(properties, "blockSize");
            syncInterval = getIntOrThrow(properties, "syncInterval");

            String[] peersStrings = getOrThrow(properties, "peers").split(",");

            peers = new ArrayList<>();
            for (String s : peersStrings) {
                s = s.trim();
                if (!s.isEmpty()) {
                    HostPort hp;
                    try {
                        hp = HostPort.fromAddress(s, port).get();
                    } catch (HostPortParseException e) {
                        throw ConfigException.formatError("peers", "not a valid address: " + s);
                    }
                    peers.add(hp);
                }
            }

            initialised = true;
        } catch (FileNotFoundException __) {
            throw ConfigException.fileMissing();
        } catch (IOException e) {
            throw ConfigException.via(e);
        }
    }

    private static String getOrThrow(Properties properties, String key) throws ConfigException {
        var result = properties.getProperty(key);
        if (key == null) {
            throw ConfigException.keyMissing(key);
        }
        return result;
    }
    private static int getIntOrThrow(Properties properties, String key) throws ConfigException {
        var result = getOrThrow(properties, key);
        if (!Conversion.isInteger(result)) {
            throw ConfigException.formatError(key, "not a valid integer: " + result);
        }
        return Integer.parseInt(result);
    }

    // private constructor to prevent initialization
    private Configuration() {
    }

    public static String getMode() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return mode;
    }

    public static String getPath() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return path;
    }

    public static String getAdvertisedName() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return advertisedName;
    }

    public static int getMaximumConnections() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return maximumConnections;
    }

    public static int getBlockSize() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return blockSize;
    }

    public static int getSyncInterval() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return syncInterval;
    }

    public static int getPort() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return port;
    }

    public static int getClientPort() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return clientPort;
    }

    public static List<HostPort> getPeers() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return Collections.unmodifiableList(peers);
    }

    public static HostPort getHostPort() {
        if (!initialised) {
            throw new IllegalStateException("Must initialise configuration first");
        }
        return new HostPort(advertisedName, port);
    }
}