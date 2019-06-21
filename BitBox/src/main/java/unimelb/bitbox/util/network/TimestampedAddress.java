package unimelb.bitbox.util.network;

import functional.algebraic.Maybe;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Pairs an address with a timestamp.
 */
public class TimestampedAddress {
    private final String address;
    private String timestamp;

    public TimestampedAddress(String address) {
        this.address = address;
        timestamp = "[" + new Timestamp(new Date().getTime()) + "] ";
    }

    /**
     * Produces a timestamp that matches the given input.
     */
    public static Maybe<TimestampedAddress> parse(String line) {
        try {
            String date = line.split("\\[")[1].split("]")[0];
            while (date.length() < 23) {
                // This is like, once or twice at most.
                //noinspection StringConcatenationInLoop
                date += "0";
            }
            date = "[" + date + "] ";
            TimestampedAddress addr = new TimestampedAddress(line.split("] ")[1]);
            addr.timestamp = date;
            return Maybe.just(addr);
        } catch (ArrayIndexOutOfBoundsException e) {
            return Maybe.nothing();
        }
    }

    private String getIP() {
        return address.split(" ")[0];
    }

    @Override
    public boolean equals(Object rhs) {
        return rhs instanceof TimestampedAddress && ((TimestampedAddress) rhs).getIP().equals(getIP());
    }

    @Override
    public int hashCode() {
        return getIP().hashCode();
    }

    @Override
    public String toString() {
        return timestamp + address;
    }
}