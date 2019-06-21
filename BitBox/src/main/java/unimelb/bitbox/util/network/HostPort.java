package unimelb.bitbox.util.network;

import functional.algebraic.Result;
import unimelb.bitbox.server.PeerServer;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A class to store a host and port together.
 */
public class HostPort implements IJSONData {
    /**
     * The hostname stored
     */
    public final String hostname;
    /**
     * The port stored
     */
    public final int port;

    private final HostPort alias;

    /**
     * Create a HostPort object from a {@link JSONDocument}.
     */
    public static Result<HostPort, JSONException> fromJSON(JSONDocument doc) {
        Result<String, JSONException> host = doc.getString("host");
        Result<Long, JSONException> port = doc.getLong("port");

        return host.andThen(hostVal -> port.map(portVal -> new HostPort(hostVal, portVal)));
    }

    /**
     * Create a HostPort object from a string of the form `host:port`.
     */
    public static Result<HostPort, HostPortParseException> fromAddress(String address) {
        address = address.replace("/", "");
        if (!address.contains(":")) {
            return Result.error(new HostPortParseException("malformed host-port: " + address));
        }

        String[] parts = address.split(":");
        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return Result.error(new HostPortParseException("malformed port: " + parts[1]));
        }
        return Result.value(new HostPort(host, port, false));
    }

    private static HostPort fromAlias(String host, int port) {
        String hostUsed = host.replace("/", "");
        try {
            hostUsed = InetAddress.getByName(hostUsed).getHostAddress();
        } catch (UnknownHostException ignored) {
            PeerServer.log().warning("Unknown host " + hostUsed + ":" + port);
        }
        return new HostPort(hostUsed, port, true);
    }

    public HostPort(String host, long port) {
        this(host, (int) port, false);
    }

    public HostPort(String host, int port) {
        this(host, port, false);
    }

    private HostPort(String hostname, int port, boolean aliased) {
        // Remove slashes at the start for consistency
        this.hostname = hostname.replace("/", "");
        this.port = port;
        // If this wasn't an alias HostPort, cache an alias for later; otherwise, we are our own alias
        alias = aliased
              ? this
              : fromAlias(hostname, port);
    }

    /**
     * Return the address described by this HostPort in `host:port` form.
     */
    public String asAddress() {
        return toString();
    }

    /**
     * Return the address described by this HostPort in `host:port` form, after network resolution has been performed.
     */
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

    @Override
    public JSONDocument toJSON() {
        JSONDocument doc = new JSONDocument();
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

    /**
     * Returns whether the network resolution gave a different result.
     */
    public boolean isAliased() {
        return alias != this;
    }
}