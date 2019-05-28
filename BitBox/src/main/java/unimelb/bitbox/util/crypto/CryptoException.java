package unimelb.bitbox.util.crypto;


import unimelb.bitbox.util.functional.algebraic.Either;
import unimelb.bitbox.util.functional.algebraic.Result;
import unimelb.bitbox.util.functional.combinator.Combinators;
import unimelb.bitbox.util.network.JSONException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CryptoException extends Exception {
    private Either<JSONException, Exception> exception;

    public static <T> Result<CryptoException, T> lift(Result<JSONException, T> res) {
        return res.matchThen(err -> Result.error(new CryptoException(err)),
                             Result::value);
    }

    public CryptoException(JSONException e) {
        exception = Either.left(e);
    }

    public CryptoException(NoSuchAlgorithmException e) {
        exception = Either.right(e);
    }
    public CryptoException(InvalidKeyException e) {
        exception = Either.right(e);
    }
    public CryptoException(NoSuchPaddingException e) {
        exception = Either.right(e);
    }
    public CryptoException(BadPaddingException e) {
        exception = Either.right(e);
    }
    public CryptoException(IllegalBlockSizeException e) {
        exception = Either.right(e);
    }

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