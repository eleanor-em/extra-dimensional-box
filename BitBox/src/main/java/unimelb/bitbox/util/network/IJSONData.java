package unimelb.bitbox.util.network;

import org.jetbrains.annotations.Contract;

@FunctionalInterface
public interface IJSONData {
    @Contract(pure = true)
    JSONDocument toJSON();

    @Contract(pure = true)
    default String encode() {
        return toJSON().toString();
    }

    default String networkEncode() {
        return encode() + "\n";
    }
}
