package unimelb.bitbox.util.crypto;


import functional.algebraic.Either;
import functional.combinator.Combinators;
import unimelb.bitbox.util.network.JSONException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * An exception that can occur during cryptographic operations.
 */
public class CryptoException extends Exception {
    private final Either<JSONException, Exception> exception;

    public CryptoException(JSONException e) {
        exception = Either.left(e);
    }

    CryptoException(NoSuchAlgorithmException e) {
        exception = Either.right(e);
    }
    CryptoException(InvalidKeyException e) {
        exception = Either.right(e);
    }
    CryptoException(NoSuchPaddingException e) {
        exception = Either.right(e);
    }
    CryptoException(BadPaddingException e) {
        exception = Either.right(e);
    }
    CryptoException(IllegalBlockSizeException e) {
        exception = Either.right(e);
    }

    /**
     * Returns the exception that caused this exception.
     */
    public Exception getCause() {
        return exception.matchThen(Combinators::id, Combinators::id);
    }

    @Override
    public String getMessage() {
        return getCause().getMessage();
    }

    @Override
    public void printStackTrace() {
        getCause().printStackTrace();
    }
}