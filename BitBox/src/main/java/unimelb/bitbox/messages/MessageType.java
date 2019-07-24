package unimelb.bitbox.messages;

import functional.algebraic.Result;
import unimelb.bitbox.util.network.JSONException;

/**
 * The different types of message.
 *
 * @author Eleanor McMurtry
 */
public enum MessageType {
    INVALID_PROTOCOL,
    CONNECTION_REFUSED,
    HANDSHAKE_REQUEST,
    HANDSHAKE_RESPONSE,
    FILE_CREATE_REQUEST,
    FILE_CREATE_RESPONSE,
    FILE_MODIFY_REQUEST,
    FILE_MODIFY_RESPONSE,
    FILE_BYTES_REQUEST,
    FILE_BYTES_RESPONSE,
    FILE_DELETE_REQUEST,
    FILE_DELETE_RESPONSE,
    DIRECTORY_CREATE_REQUEST,
    DIRECTORY_CREATE_RESPONSE,
    DIRECTORY_DELETE_REQUEST,
    DIRECTORY_DELETE_RESPONSE;

    /**
     * Convert a String to a MessageType.
     * @return the MessageType or a parsing exception
     */
    public static Result<MessageType, JSONException> fromString(String str) {
        return Result.ofRuntime(() -> valueOf(str))
                     .mapError(ignored -> new JSONException("command not recognised"));
    }
}
