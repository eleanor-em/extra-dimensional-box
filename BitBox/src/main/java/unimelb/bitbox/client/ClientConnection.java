package unimelb.bitbox.client;

import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.JSONDocument;

import javax.crypto.SecretKey;
import java.net.Socket;
import java.util.function.Function;

/**
 * Stores data about a client connection.
 */
class ClientConnection {
    private final Socket socket;
    private boolean authenticated = false;
    private boolean sentKey = false;
    private String ident;
    private SecretKey key = null;

    private boolean anonymous = true;

    /**
     * Initialise a client given an accepted socket
     */
    ClientConnection(Socket socket) {
        this.socket = socket;
        ident = "<unknown>";
    }

    /**
     * @return the socket object attached to this client
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * @return whether this client connection has been authenticated
     */
    boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * @return the host:port address of this client
     */
    public String getAddress() {
        return socket.getInetAddress().toString().replace("/", "") + ":" + socket.getPort();
    }

    /**
     * Sets the identity if the client hasn't already had an identity set.
     * @param ident the identity to set to
     */
    void setIdent(String ident) {
        if (anonymous) {
            this.ident = ident;
            anonymous = false;
        }
    }

    /**
     * @return the client's identity, &lt;unknown&gt; if the identity has not yet been set
     */
    String getIdent() {
        return ident;
    }

    /**
     * Authenticates the client, using the given key as a session key
     * @param key the session key
     */
    void authenticate(SecretKey key) {
        if (!anonymous) {
            authenticated = true;
            assert key != null;
            this.key = key;
        }
    }

    /**
     * Binds an operation that produces a JSON document to the session key, performing it only if the key exists.
     * @param op the operation
     * @return a Result representing the operation's outcome, or a failure response if the client is not authenticated
     */
    <E extends Exception> Result<E, JSONDocument> bindKey(Function<? super SecretKey, ? extends Result<E, JSONDocument>> op) {
        if (authenticated) {
            return op.apply(key);
        }
        return Result.value(ClientServer.generateFailResponse("client not authenticated"));
    }

    /**
     * Maps a function that produces a JSON document to the session key.
     * @param op the function to map
     * @return the result of the function if the client is authenticated, and a failure response otherwise
     */
    JSONDocument mapKey(Function<? super SecretKey, ? extends JSONDocument> op) {
        if (authenticated) {
            return op.apply(key);
        }
        return ClientServer.generateFailResponse("client not authenticated");
    }

    /**
     * Informs the client that the key has been sent over the connection.
     * @return whether the client had already sent the key
     */
    boolean sentKey() {
        boolean ret = sentKey;
        sentKey = true;
        return ret;
    }

    @Override
    public String toString() {
        if (!authenticated) {
            return ident + " (unauthenticated)";
        }
        return ident;
    }

    @Override
    public boolean equals(Object rhs) {
        return rhs instanceof ClientConnection && rhs.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}