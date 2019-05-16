package unimelb.bitbox.messages;

import unimelb.bitbox.util.FileSystemManager;

public class InvalidProtocol extends Message {
    public InvalidProtocol(String message) {
        document.append("command", INVALID_PROTOCOL);
        document.append("message", message);
    }
}
