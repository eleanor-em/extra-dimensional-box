package unimelb.bitbox.util;

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
            throws CryptoException {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException(e);
        }
    }

    private static SecureRandom rand;
    private static void assignCRNG() throws CryptoException {
        if (rand == null) {
            try {
                rand = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                throw new CryptoException(e);
            }
        }
    }

    /**
     * Encrypts the provided secret key with the provided public key, and returns a base-64 encoding of the ciphertext.
     */
    public static String encryptSecretKey(SecretKey secretKey, PublicKey publicKey)
            throws CryptoException {
        try {
            assignCRNG();

            Cipher encipher = Cipher.getInstance("RSA/ECB/NoPadding");
            encipher.init(Cipher.PUBLIC_KEY, publicKey);
            byte[] padding = new byte[240];
            rand.nextBytes(padding);
            byte[] input = Arrays.copyOf(secretKey.getEncoded(), 255);
            System.arraycopy(padding, 0, input, 16, 239);
            byte[] encrypted = encipher.doFinal(input);
            return new String(Base64.getEncoder().encode(encrypted));
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
    }

    /**
     * Decrypts a received message of the form {"payload":"CIPHERTEXT"}.
     * Returns the decrypted ciphertext.
     */
    public static JsonDocument decryptMessage(SecretKey secretKey, JsonDocument message)
            throws CryptoException, ResponseFormatException {
        // Safely extract the payload
        String payload = message.require("payload");

        String result;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            result = new String(cipher.doFinal(Base64.getDecoder().decode(payload)));
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
        return JsonDocument.parse(result);
    }

    /**
     * Returns a cryptographically secure random number between min and max (inclusive)
     * @param min the minimum value to return
     * @param max the maximum value to return
     * @return the generated number
     */
    public static int cryptoRandRange(int min, int max) throws CryptoException {
        assignCRNG();
        // + 1 because nextInt(bound) is exclusive
        return min + rand.nextInt(max - min + 1);
    }

    /**
     * Encrypts a prepared message that has been encoded in JSON.
     * Returns a JSON message ready to be sent of the form {"payload":"CIPHERTEXT"}.
     */
    public static JsonDocument encryptMessage(SecretKey secretKey, JsonDocument message)
            throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            StringBuilder padding = new StringBuilder();
            message.append("padding", "");

            // The number of bytes we need is 16 minus the remainder, so that we end up with a multiple of 16 total
            // I would prefer to do this by negating the value, but Java helpfully gives you a signed result...
            int messageSize = message.toJson().length();
            if (messageSize % 16 != 0) {
                int requiredBytes = 16 - message.toJson().length() % 16;
                for (int i = 0; i < requiredBytes; ++i) {
                    // Printable character range is 32-126. Trust me on this.
                    char next = (char) cryptoRandRange(32, 126);

                    // Crap, we can't use a quote or a backslash for JSON.
                    // Just try again if this happens
                    if (next == '"' || next == '\\') {
                        --i;
                    } else {
                        padding.append(next);
                    }
                }
                message.append("padding", padding.toString());
            }

            JsonDocument encrypted = new JsonDocument();
            encrypted.append("payload", Base64.getEncoder().encodeToString(cipher.doFinal(message.toJson().getBytes())));
            return encrypted;
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
    }
}
