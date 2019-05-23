package unimelb.bitbox.util;

import org.json.simple.parser.ParseException;

import javax.crypto.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
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

    private static SecureRandom rand;
    private static void assignCRNG() throws NoSuchAlgorithmException {
        if (rand == null) {
            rand = SecureRandom.getInstanceStrong();
        }
    }

    /**
     * Encrypts the provided secret key with the provided public key, and returns a base-64 encoding of the ciphertext.
     */
    public static String encryptSecretKey(SecretKey secretKey, PublicKey publicKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException {
        assignCRNG();

        Cipher encipher = Cipher.getInstance("RSA/ECB/NoPadding");
        encipher.init(Cipher.PUBLIC_KEY, publicKey);
        byte[] padding = new byte[240];
        rand.nextBytes(padding);
        byte[] input = Arrays.copyOf(secretKey.getEncoded(), 255);
        System.arraycopy(padding, 0, input, 16, 239);
        byte[] encrypted = encipher.doFinal(input);
        return new String(Base64.getEncoder().encode(encrypted));
    }

    /**
     * Decrypts a received message of the form {"payload":"CIPHERTEXT"}.
     * Returns the decrypted ciphertext.
     */
    public static String decryptMessage(SecretKey secretKey, String message)
            throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException,
            ResponseFormatException, ParseException, InvalidKeyException {
        // Safely extract the payload
        JsonDocument received = JsonDocument.parse(message);
        String payload = received.require("payload");

        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        String result = new String(cipher.doFinal(Base64.getDecoder().decode(payload)));
        int end = result.lastIndexOf('}');
        if (end != -1) {
            return result.substring(0, end + 1);
        }
        return result;
    }

    /**
     * Encrypts a prepared message that has been encoded in JSON.
     * Returns a JSON message ready to be sent of the form {"payload":"CIPHERTEXT"}.
     */
    public static String encryptMessage(SecretKey secretKey, String message)
            throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException,
            IllegalBlockSizeException, InvalidKeyException {
        assignCRNG();
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        StringBuilder messageBuilder = new StringBuilder(message);
        while (messageBuilder.length() % 16 != 0) {
            messageBuilder.append((char) (('A' + rand.nextInt(57))));
        }
        message = messageBuilder.toString();

        JsonDocument encrypted = new JsonDocument();
        encrypted.append("payload", Base64.getEncoder().encodeToString(cipher.doFinal(message.getBytes())));
        return encrypted.toJson();
    }
}
