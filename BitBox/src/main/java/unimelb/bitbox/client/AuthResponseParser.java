package unimelb.bitbox.client;

import unimelb.bitbox.util.Crypto;
import unimelb.bitbox.util.CryptoException;
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
    private String message;

    /**
     * Extract the data from the provided message.
     * @param message the JSON data to interpret
     * @throws ResponseFormatException in case the provided message is malformed
     */
    public AuthResponseParser(String message) throws ResponseFormatException {
        JsonDocument doc;
        doc = JsonDocument.parse(message);

        status = doc.require("status");
        if (status) {
            String keyVal = doc.require("AES128");
            key = Base64.getDecoder().decode(keyVal);
        }
        this.message = doc.require("message");
    }

    /**
     * Decrypts the received secret key using the provided private key.
     * See {@link unimelb.bitbox.util.Crypto#encryptSecretKey} for details on the quirks involved.
     *
     * @param privateKey the private key to use for decryption
     * @return the decrypted secret key
     * @throws CryptoException  in case of a cryptography error
     */
    public SecretKey decryptKey(PrivateKey privateKey)
            throws CryptoException {
        if (key != null) {
            try {
                Cipher decipher = Cipher.getInstance("RSA/ECB/NoPadding");
                decipher.init(Cipher.PRIVATE_KEY, privateKey);
                byte[] decrypted = decipher.doFinal(key);
                // Ignore any null bytes prepended to the key
                int nullHeader = 0;
                while (decrypted[nullHeader] == 0) {
                    ++nullHeader;
                }
                return new SecretKeySpec(decrypted, nullHeader, Crypto.AES_KEY_BYTES, "AES");
            } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
                throw new CryptoException(e);
            }
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
