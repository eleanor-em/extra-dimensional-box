package unimelb.bitbox.messages;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.JSONData;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

/*
 * Base class for all Messages that peers can send.
 * Optionally, a peer can provide its friendly name (e.g. Alice-localhost:8111) for debugging.
 */
public abstract class Message implements JSONData {
    public static final String INVALID_PROTOCOL = "INVALID_PROTOCOL";
    public static final String CONNECTION_REFUSED = "CONNECTION_REFUSED";
    public static final String HANDSHAKE_REQUEST = "HANDSHAKE_REQUEST";
    public static final String HANDSHAKE_RESPONSE = "HANDSHAKE_RESPONSE";
    public static final String FILE_CREATE_REQUEST = "FILE_CREATE_REQUEST";
    public static final String FILE_CREATE_RESPONSE = "FILE_CREATE_RESPONSE";
    public static final String FILE_MODIFY_REQUEST = "FILE_MODIFY_REQUEST";
    public static final String FILE_MODIFY_RESPONSE = "FILE_MODIFY_RESPONSE";
    public static final String FILE_BYTES_REQUEST = "FILE_BYTES_REQUEST";
    public static final String FILE_BYTES_RESPONSE = "FILE_BYTES_RESPONSE";
    public static final String FILE_DELETE_REQUEST = "FILE_DELETE_REQUEST";
    public static final String FILE_DELETE_RESPONSE = "FILE_DELETE_RESPONSE";
    public static final String DIRECTORY_CREATE_REQUEST = "DIRECTORY_CREATE_REQUEST";
    public static final String DIRECTORY_CREATE_RESPONSE = "DIRECTORY_CREATE_RESPONSE";
    public static final String DIRECTORY_DELETE_REQUEST = "DIRECTORY_DELETE_REQUEST";
    public static final String DIRECTORY_DELETE_RESPONSE = "DIRECTORY_DELETE_RESPONSE";

    protected JSONDocument document;
    private String summary;

    public Message(String summary) {
        this.summary = summary;

        document = new JSONDocument();
    }

    public void setFriendlyName(String name) {
        if (!document.containsKey("friendlyName")) {
            document.append("friendlyName", name);
        }
    }

    public Result<JSONException, String> getCommand() {
        return document.get("command");
    }

    public boolean isRequest() {
        return getCommand().orElse("").contains("REQUEST");
    }

    public String getSummary() {
        return summary;
    }

    public final void reportErrors() {
        document.getBoolean("status")
                .ok(status -> {
                    if (status) {
                        Result.of(() -> PeerServer.logWarning("Sending failed " + getCommand() + ": " + document.get("message")))
                                .err(e -> PeerServer.logWarning("Malformed message: " + e.getMessage()));
                    }
                });
    }

    public final JSONDocument toJSON() {
        // If this had a status code, report any errors
        reportErrors();
        return document;
    }

    @Override
    public boolean equals(Object rhs) {
        return rhs instanceof Message && ((Message) rhs).document.equals(document);
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
