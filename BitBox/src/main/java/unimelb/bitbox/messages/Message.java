package unimelb.bitbox.messages;

import functional.algebraic.Result;
import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.network.IJSONData;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

/**
 * Base class for all Messages that peers can send.
 * Optionally, a peer can provide its friendly name (e.g. Alice-localhost:8111) for debugging.
 *
 * @author Eleanor McMurtry
 */
public abstract class Message implements IJSONData {
    protected JSONDocument document;
    private final String summary;

    Message(String summary) {
        this.summary = summary;

        document = new JSONDocument();
    }

    public void setFriendlyName(String name) {
        document.appendIfMissing("friendlyName", name);
    }

    public Result<MessageType, JSONException> getCommand() {
        return document.getString("command")
                       .andThen(MessageType::fromString);
    }

    public boolean isRequest() {
        return getCommand().map(c -> c.name().contains("REQUEST")).orElse(false);
    }

    public String getSummary() {
        return summary;
    }

    public final void reportErrors() {
        document.getBoolean("status")
                .ifOk(status -> {
                    if (!status) {
                        Result.of(() -> {
                            String command = getCommand().get().name();
                            String message = document.getString("message").get();
                            if (!message.contains("already exists")) {
                                PeerServer.log().warning("Sending failed " + command + ": " + message);
                            }
                        }).ifErr(e -> PeerServer.log().warning("Malformed message: " + e.getMessage()));
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
