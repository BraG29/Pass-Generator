package com.brag.oauthexample.services;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Service
public class PasswordService {

    private final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final String DIGITS = "0123456789";
    private final String SYMBOLS = "!@#$%^&*()-_=+<>?";
    private final String ALL_CHARS = LOWERCASE + UPPERCASE + DIGITS + SYMBOLS;

    private SecureRandom random = new SecureRandom();

    private final Cipher cipher;
    private final SecretKey secretKey;

    public PasswordService(Cipher cipher, SecretKey secretKey) {
        this.cipher = cipher;
        this.secretKey = secretKey;
    }

    public String generatePassword(int length) throws IllegalArgumentException {
        List<Character> passwordChars = new ArrayList<>();

        passwordChars.add(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        passwordChars.add(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        passwordChars.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
        passwordChars.add(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));

        for(int i = 4; i < length; i++) {
            passwordChars.add(ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length())));
        }

        Collections.shuffle(passwordChars);
        StringBuilder password = new StringBuilder();

        for(char c : passwordChars) {
            password.append(c);
        }

        return password.toString();
    }

    public String encryptPassword(String password) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedPass = cipher.doFinal(password.getBytes());

            return Base64.getEncoder().encodeToString(encryptedPass);

        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    public String decryptPassword(String encryptedPassword) {
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedPass = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));

            return new String(decryptedPass);

        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getStringKey(){
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

}
