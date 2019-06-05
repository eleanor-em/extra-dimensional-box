package unimelb.bitbox.messages;

import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.network.JSONException;

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

    public static Result<JSONException, MessageType> fromString(String str) {
        return Result.ofRuntime(() -> valueOf(str))
                     .mapError(ignored -> new JSONException("command not recognised"));
    }
}
