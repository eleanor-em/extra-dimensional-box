package unimelb.bitbox.util.network;

@FunctionalInterface
public interface IJSONData {
    JSONDocument toJSON();

    default String encode() {
        return toJSON().toString();
    }
    default String networkEncode() {
        return encode() + "\n";
    }
}
