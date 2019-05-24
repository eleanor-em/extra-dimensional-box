package unimelb.bitbox.util;

import unimelb.bitbox.ServerMain;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Like the one Aaron gave us, except it actually does error checking. Amazing!
 */
public class HostPort {
    public final String hostname;
    public final int port;

    private HostPort alias;

    public static HostPort fromJSON(JsonDocument doc) throws ResponseFormatException {
        String host = doc.require("host");
        long port = doc.require("port");

        return new HostPort(host, (int)port);
    }

    public static HostPort fromAddress(String address) throws HostPortParseException {
        address = address.replace("/", "");
        if (!address.contains(":")) {
            throw new HostPortParseException("malformed host-port: " + address);
        }

        String[] parts = address.split(":");
        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new HostPortParseException("malformed port: " + parts[1]);
        }
        return new HostPort(host, port, false);
    }

    public static HostPort fromAlias(String host, int port) {
        String hostUsed = host.replace("/", "");
        try {
            hostUsed = InetAddress.getByName(hostUsed).getHostAddress();
        } catch (UnknownHostException ignored) {
            ServerMain.log.warning("Unknown host " + hostUsed + ":" + port);
        }
        return new HostPort(hostUsed, port, true);
    }

    public HostPort(String host, int port) {
        this(host, port, false);
    }

    private HostPort(String hostname, int port, boolean aliased) {
        // Remove slashes at the start for consistency
        this.hostname = hostname.replace("/", "");
        this.port = port;
        // If this wasn't an alias HostPort, cache an alias for later; otherwise, we are our own alias
        if (!aliased) {
            this.alias = fromAlias(hostname, port);
        } else {
            this.alias = this;
        }
    }

    public String asAddress() {
        return toString();
    }
    public String asAliasedAddress() {
        return alias.toString();
    }

    @Override
    public String toString() {
        return hostname + ":" + port;
    }

    @Override
    public boolean equals(Object rhs) {
        return (rhs instanceof HostPort) && (rhs.toString().equals(toString()));
    }

    public JsonDocument toJSON() {
        JsonDocument doc = new JsonDocument();
        doc.append("host", hostname);
        doc.append("port", port);
        return doc;
    }

    /**
     * Returns true if this HostPort is equal to the other HostPort after resolving both hostnames.
     */
    public boolean fuzzyEquals(HostPort hostPort) {
        return alias.equals(hostPort.alias);
    }
}