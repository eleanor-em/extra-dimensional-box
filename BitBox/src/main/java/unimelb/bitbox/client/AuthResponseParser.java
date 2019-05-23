package unimelb.bitbox.client;

import org.json.simple.parser.ParseException;
import unimelb.bitbox.util.JsonDocument;
import unimelb.bitbox.util.ResponseFormatException;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * Parses a received authentication response.
 */
public class AuthResponseParser {
    private boolean status;
    private byte[] key;
    private String message = "";

    /**
     * Extract the data from the provided message.
     * @param message the JSON data to interpret
     * @throws ResponseFormatException in case the provided message is malformed
     */
    public AuthResponseParser(String message) throws ResponseFormatException {
        JsonDocument doc;
        try {
            doc = JsonDocument.parse(message);
        } catch (ParseException e) {
            throw new ResponseFormatException("Error parsing message: " + e.getMessage());
        }

        status = doc.require("status");
        String keyVal = doc.require("AES128");
        key = Base64.getDecoder().decode(keyVal);
        this.message = doc.require("message");
    }

    /**
     * Decrypts the received secret key using the provided private key.
     * @param privateKey the private key to use for decryption
     * @return the decrypted secret key
     * @throws NoSuchPaddingException    in case of a cryptography error
     * @throws NoSuchAlgorithmException  in case of a cryptography error
     * @throws InvalidKeyException       in case of a cryptography error
     * @throws BadPaddingException       in case of a cryptography error
     * @throws IllegalBlockSizeException in case of a cryptography error
     */
    public SecretKey decryptKey(PrivateKey privateKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
                   BadPaddingException, IllegalBlockSizeException {
        if (key != null) {
            Cipher decipher = Cipher.getInstance("RSA/ECB/NoPadding");
            decipher.init(Cipher.PRIVATE_KEY, privateKey);
            byte[] decrypted = decipher.doFinal(key);
            return new SecretKeySpec(decrypted, 1, 16, "AES");
        }
        return null;
    }

    /**
     * @return whether the response is in an error state
     */
    public boolean isError() {
        return !status;
    }

    /**
     * @return the message provided with the response, if there is one; otherwise, empty string
     */
    public String getMessage() {
        return message;
    }
}
