package unimelb.bitbox.client;

import java.util.stream.Stream;

/**
 * @author Eleanor McMurtry
 */
public enum ClientCommand {
    STOP,
    PING,
    LIST,
    START;

    public static boolean isValid(String command) {
        return Stream.of(values())
                .map(Enum::toString)
                .anyMatch(c -> c.equals(command));
    }
}
