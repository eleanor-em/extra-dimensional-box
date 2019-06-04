package unimelb.bitbox.util.network;

public interface IJSONData {
    JSONDocument toJSON();

    default String encode() {
        return toJSON().toString();
    }
    default String networkEncode() {
        return encode() + "\n";
    }
}
