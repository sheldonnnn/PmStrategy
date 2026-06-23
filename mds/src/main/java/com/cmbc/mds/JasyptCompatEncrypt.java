package com.cmbc.mds;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class JasyptCompatEncrypt {
    private static final String ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";
    private static final int ITERATIONS = 1000;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 16;
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private static final String JASYPT_PASSWORD = "fxquote-jasypt-key";
    private static final String PLAIN_TEXT = "1qaz@WSX";
    private static final String ENCRYPTED_TEXT =
            "ENC(U1p7D/OhhVdQeyf1gqBS2zVuBV9B0Cdm1toeFzpLD18d65RN4dTXY+MCtqJsE7V3)";

//    public static void main(String[] args) throws Exception {
//        System.out.println("Encrypted value:");
//        System.out.println(encrypt(JASYPT_PASSWORD, PLAIN_TEXT));
//
//        System.out.println("Decrypted value:");
//        System.out.println(decrypt(JASYPT_PASSWORD, ENCRYPTED_TEXT));
//    }

    private static String encrypt(String password, String plaintext) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        secureRandom.nextBytes(iv);

        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        SecretKey key = factory.generateSecret(new PBEKeySpec(password.toCharArray()));
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(salt, ITERATIONS, new IvParameterSpec(iv)));

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] output = new byte[salt.length + iv.length + encrypted.length];
        System.arraycopy(salt, 0, output, 0, salt.length);
        System.arraycopy(iv, 0, output, salt.length, iv.length);
        System.arraycopy(encrypted, 0, output, salt.length + iv.length, encrypted.length);
        return "ENC(" + Base64.getEncoder().encodeToString(output) + ")";
    }

    private static String decrypt(String password, String encryptedValue) throws Exception {
        byte[] payload = Base64.getDecoder().decode(unwrapEncryptedValue(encryptedValue));
        if (payload.length <= SALT_LENGTH + IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted value is too short.");
        }

        byte[] salt = Arrays.copyOfRange(payload, 0, SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(payload, SALT_LENGTH, SALT_LENGTH + IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(payload, SALT_LENGTH + IV_LENGTH, payload.length);

        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        SecretKey key = factory.generateSecret(new PBEKeySpec(password.toCharArray()));
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(salt, ITERATIONS, new IvParameterSpec(iv)));

        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static String unwrapEncryptedValue(String encryptedValue) {
        if (encryptedValue.startsWith(ENC_PREFIX) && encryptedValue.endsWith(ENC_SUFFIX)) {
            return encryptedValue.substring(ENC_PREFIX.length(), encryptedValue.length() - ENC_SUFFIX.length());
        }
        return encryptedValue;
    }

}
