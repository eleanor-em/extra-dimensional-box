package unimelb.bitbox.util.crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a ssh-keygen public key from a string, and stores the relevant data.
 *
 * Based on code by stackoverflow user Taras: https://stackoverflow.com/a/54600720/4672696
 */
public class SSHPublicKey {
    private static final int VALUE_LENGTH = 4;
    private static final byte[] INITIAL_PREFIX = {0x00, 0x00, 0x00, 0x07, 0x73, 0x73, 0x68, 0x2d, 0x72, 0x73, 0x61};
    private static final Pattern SSH_RSA_PATTERN = Pattern.compile("ssh-rsa[\\s]+([A-Za-z0-9/+]+=*)[\\s]+(.*)");

    private final String keyString;
    private final PublicKey key;
    public PublicKey getKey() {
        return key;
    }

    private final String ident;
    public String getIdent() {
        return ident;
    }

    /**
     * Load the key from the string.
     *
     // SSH-RSA key format
     //
     //        00 00 00 07             The length in bytes of the next field
     //        73 73 68 2d 72 73 61    The key type (ASCII encoding of "ssh-rsa")
     //        00 00 00 03             The length in bytes of the public exponent
     //        01 00 01                The public exponent (usually 65537, as here)
     //        00 00 01 01             The length in bytes of the modulus (here, 257)
     //        00 c3 a3...             The modulus
     * @param keyString the string to load
     * @throws InvalidKeyException in case the key is not a valid key
     */
    public SSHPublicKey(String keyString) throws InvalidKeyException {
        this.keyString = keyString;

        Matcher matcher = SSH_RSA_PATTERN.matcher(keyString.trim());
        if (!matcher.matches()) {
            throw new InvalidKeyException("Key format is invalid for SSH RSA");
        }
        String keyStr = matcher.group(1);
        ident = matcher.group(2);

        ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(keyStr));

        byte[] prefix = new byte[INITIAL_PREFIX.length];

        try {
            if (INITIAL_PREFIX.length != is.read(prefix) || !Arrays.equals(INITIAL_PREFIX, prefix)) {
                throw new InvalidKeyException("Initial [ssh-rsa] key prefix missing");
            }

            BigInteger exponent = getValue(is);
            BigInteger modulus = getValue(is);

            key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new InvalidKeyException("Failed to read SSH RSA certificate from string", e);
        }
    }

    private static BigInteger getValue(InputStream is) throws IOException {
        byte[] lenBuff = new byte[VALUE_LENGTH];
        if (is.read(lenBuff) != VALUE_LENGTH) {
            throw new InvalidParameterException("Unable to read value length");
        }

        int len = ByteBuffer.wrap(lenBuff).getInt();
        byte[] valueArray = new byte[len];
        if (len != is.read(valueArray)) {
            throw new InvalidParameterException("Unable to read value");
        }

        return new BigInteger(valueArray);
    }

    @Override
    public String toString() {
        return keyString;
    }

    @Override
    public boolean equals(Object rhs) {
        return rhs instanceof SSHPublicKey && rhs.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}