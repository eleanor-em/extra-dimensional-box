package unimelb.bitbox.client;

import unimelb.bitbox.util.Document;
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
public class AuthResponse {
    private boolean status;
    private byte[] key;
    private String message = "";

    /**
     * Extract the data from the provided message.
     * @param message the JSON data to interpret
     * @throws ResponseFormatException in case the provided message is malformed
     */
    public AuthResponse(String message) throws ResponseFormatException {
        Document doc = Document.parse(message);

        // Safely check the status
        if (doc.containsKey("status")) {
            Object statusVal = doc.get("status");
            if (statusVal instanceof Boolean) {
                status = (boolean) statusVal;
            } else {
                throw new ResponseFormatException("status field malformed");
            }
        } else {
            throw new ResponseFormatException("status field missing");
        }

        // Safely extract the key
        if (status) {
            if (doc.containsKey("AES128")) {
                Object keyVal = doc.get("AES128");
                if (keyVal instanceof String) {
                    key = Base64.getDecoder().decode((String) keyVal);
                } else {
                    throw new ResponseFormatException("AES128 field malformed");
                }
            } else {
                throw new ResponseFormatException("AES128 field missing");
            }
        }

        // Safely extract the message for user information in case of failure
        Object messageVal = doc.get("message");
        if (messageVal instanceof String) {
            message = (String)messageVal;
        }
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
            Cipher decipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            decipher.init(Cipher.PRIVATE_KEY, privateKey);
            byte[] decrypted = decipher.doFinal(key);
            return new SecretKeySpec(decrypted, 0, decrypted.length, "AES");
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
