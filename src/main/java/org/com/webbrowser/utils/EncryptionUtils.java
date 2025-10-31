package org.com.webbrowser.utils;

import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class EncryptionUtils {

    static Dotenv dotenv = Dotenv.configure()
            .ignoreIfMalformed()
            .ignoreIfMissing()
            .load();

    private static final String SECRET_KEY_1 = dotenv.get("SECRET_KEY_1");
    private static final String SECRET_KEY_2 = dotenv.get("SECRET_KEY_2");

    private static String encryptAES(String plainText, String keyStr) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyStr.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes()));
    }

    private static String decryptAES(String cipherText, String keyStr) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyStr.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)));
    }

    public static String encrypt(String plainText) {
        try {
            String first = encryptAES(plainText, SECRET_KEY_1);
            return encryptAES(first, SECRET_KEY_2);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String decrypt(String encryptedText) {
        try {
            String first = decryptAES(encryptedText, SECRET_KEY_2);
            return decryptAES(first, SECRET_KEY_1);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
