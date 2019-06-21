package unimelb.bitbox.util.network;

import org.jetbrains.annotations.Contract;

/**
 * An object that can be converted to a {@link JSONDocument}.
 */
@FunctionalInterface
public interface IJSONData {
    /**
     * Perform the conversion.
     * Should be pure.
     */
    @Contract(pure = true)
    JSONDocument toJSON();

    /**
     * Perform the conversion, and then convert the {@link JSONDocument} to a string.
     * Should be pure.
     */
    @Contract(pure = true)
    default String encode() {
        return toJSON().toString();
    }

    /**
     * Perform the conversion, and then convert the {@link JSONDocument} to a string, ready to be transmitted over
     * the net work.
     *
     * *Not* guaranteed to be pure, as additional processing may need to be performed.
     */
    default String networkEncode() {
        return encode() + "\n";
    }
}
