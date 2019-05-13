package unimelb.bitbox.util;

import javax.crypto.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Contains utility methods for working with the cryptography library.
 */
public class Crypto {
    /**
     * Generates a secret AES key.
     */
    public static SecretKey generateSecretKey()
            throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    /**
     * Encrypts the provided secret key with the provided public key, and returns a base-64 encoding of the ciphertext.
     */
    public static String encryptSecretKey(SecretKey secretKey, PublicKey publicKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException {
        Cipher encipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encipher.init(Cipher.PUBLIC_KEY, publicKey);
        byte[] encrypted = encipher.doFinal(secretKey.getEncoded());
        return new String(Base64.getEncoder().encode(encrypted));
    }

    /**
     * Decrypts a received message of the form {"payload":"CIPHERTEXT"}.
     * Returns the decrypted ciphertext.
     */
    public static String decryptMessage(SecretKey secretKey, String message)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException, ResponseFormatException {
        // Safely extract the payload
        Document received = Document.parse(message);
        if (!received.containsKey("payload")) {
            throw new ResponseFormatException("Received message does not contain payload");
        }
        Object payloadVal = received.get("payload");
        if (!(payloadVal instanceof String)) {
            throw new ResponseFormatException("Received message contains malformed payload");
        }
        String payload = (String)payloadVal;

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new String(cipher.doFinal(Base64.getDecoder().decode(payload.getBytes())));
    }

    /**
     * Encrypts a prepared message that has been encoded in JSON.
     * Returns a JSON message ready to be sent of the form {"payload":"CIPHERTEXT"}.
     */
    public static String encryptMessage(SecretKey secretKey, String message)
            throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException,
            IllegalBlockSizeException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        Document encrypted = new Document();
        encrypted.append("payload", Base64.getEncoder().encodeToString(cipher.doFinal(message.getBytes())));
        return encrypted.toJson();
    }
}
