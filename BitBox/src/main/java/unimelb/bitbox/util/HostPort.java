package unimelb.bitbox.util;

/**
 * Like the one Aaron gave us, except it actually does error checking. Amazing!
 */
public class HostPort {
    public final String hostname;
    public final int port;

    public HostPort(String address)
            throws IllegalArgumentException {
        if (!address.contains(":")) {
            throw new IllegalArgumentException("malformed host-port: " + address);
        }

        String[] parts = address.split(":");
        hostname = parts[0];
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed port: " + parts[1]);
        }
    }
}