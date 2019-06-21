package unimelb.bitbox.util.network;


import functional.algebraic.Maybe;
import org.json.simple.parser.ParseException;

/**
 * Thrown in case of a malformed response from a peer.
 *
 * @author Eleanor McMurtry
 */
public class JSONException extends Exception {
    private final Maybe<ParseException> cause;

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        cause.consume(err -> {
            System.out.println("Caused by:");
            err.printStackTrace();
        });
    }

    public JSONException(String message) {
        super("JSON document invalid: " + message);
        cause = Maybe.nothing();
    }
    public JSONException(String json, ParseException cause) {
        super("Error parsing JSON string `" + json + "`:" + cause);
        this.cause = Maybe.just(cause);
    }
}
