package unimelb.bitbox.util.network;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.functional.algebraic.Result;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Like the one Aaron gave us, except it actually does error checking. Amazing!
 */
public class HostPort {
    public final String hostname;
    public final int port;

    private final HostPort alias;

    public static Result<JSONException, HostPort> fromJSON(JSONDocument doc) {
        Result<JSONException, String> host = doc.getString("host");
        Result<JSONException, Long> port = doc.getLong("port");

        return host.andThen(hostVal -> port.map(portVal -> new HostPort(hostVal, portVal)));
    }

    public static Result<HostPortParseException, HostPort> fromAddress(String address) {
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

    public boolean isAliased() {
        return alias != this;
    }
}