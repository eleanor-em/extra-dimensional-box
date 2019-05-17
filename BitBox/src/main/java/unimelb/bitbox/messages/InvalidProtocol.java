package unimelb.bitbox.messages;

public class InvalidProtocol extends Message {
    public InvalidProtocol(String message) {
        super("INVALID");
        document.append("command", INVALID_PROTOCOL);
        document.append("message", message);
    }
}
