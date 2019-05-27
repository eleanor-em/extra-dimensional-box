package unimelb.bitbox.util.network;

public interface JSONData {
    JSONDocument toJSON();

    default void fromJSON(JSONDocument doc) {}

    default String encode() {
        return toJSON().toString();
    }
    default String networkEncode() {
        return encode() + "\n";
    }
}
