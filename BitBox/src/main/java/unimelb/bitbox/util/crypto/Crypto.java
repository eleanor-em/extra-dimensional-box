package unimelb.bitbox.util.crypto;

import unimelb.bitbox.util.network.JsonDocument;
import unimelb.bitbox.util.network.ResponseFormatException;

import javax.crypto.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Contains utility methods for working with the cryptography library.
 */
public class Crypto {
    private static final int AES_KEY_BITS = 128;
    public static final int AES_KEY_BYTES = AES_KEY_BITS / 8;
    public static final int RSA_KEY_BYTES = 256;

    /**
     * Generates a secret AES key.
     */
    public static SecretKey generateSecretKey()
            throws CryptoException {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_BITS);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException(e);
        }
    }

    private static SecureRandom rand;
    private static synchronized void assignCSRNG() {
        if (rand == null) {
            rand = new SecureRandom();
        }
    }

    /**
     * Encrypts the provided secret key with the provided public key, and returns a base-64 encoding of the ciphertext.
     */
    public static String encryptSecretKey(SecretKey secretKey, PublicKey publicKey)
            throws CryptoException {
        try {
            assignCSRNG();

            Cipher encipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            encipher.init(Cipher.PUBLIC_KEY, publicKey);
            // We need to pad the message to 256 bytes total, and Aaron has requested that the padding data be random
            // and *appended* to the message. However, if the message does not have a leading null byte, there's a very
            // good chance that it will be larger than the RSA modulus when converted to a BigInteger. This causes a
            // BadPaddingException. To this end, we simply provide a total message that is 255 bytes long, and allow
            // Java to prepend a null byte.
            byte[] encrypted = encipher.doFinal(secretKey.getEncoded());
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
            // Padding comes after a newline character
            result = result.split("\n")[0];
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
    public static int cryptoRandRange(int min, int max) {
        assignCSRNG();
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

            // Pad the message (if necessary)
            StringBuilder paddedMessage = new StringBuilder(message.toJson() + "\n");
            // The number of bytes we need is 16 minus the remainder, so that we end up with a multiple of 16 total
            int requiredBytes = AES_KEY_BYTES - paddedMessage.length() % AES_KEY_BYTES;
            if (requiredBytes < AES_KEY_BYTES) {
                for (int i = 0; i < requiredBytes; ++i) {
                    // Printable character range is 32-126. Trust me on this.
                    char next = (char) cryptoRandRange(32, 126);

                    // Crap, we can't use a quote or a backslash for JSON.
                    // Just try again if this happens
                    if (next == '"' || next == '\\') {
                        --i;
                    } else {
                        paddedMessage.append(next);
                    }
                }
            }
            byte[] encryptedBytes = cipher.doFinal(paddedMessage.toString().getBytes());

            JsonDocument encrypted = new JsonDocument();
            encrypted.append("payload", Base64.getEncoder().encodeToString(encryptedBytes));
            return encrypted;
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
    }
}
