package com.example.demo.services;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MicrosoftCalendarTokenCipher {

    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String VERSION_PREFIX = "v1:";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] key;

    public MicrosoftCalendarTokenCipher(
        @Value("${app.microsoft-calendar.token-encryption-key:}") String encodedKey
    ) {
        this.key = decodeKey(encodedKey);
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String clearText) {
        if (!isConfigured()) {
            throw new IllegalStateException("La clé de chiffrement Outlook Calendar n'est pas configurée.");
        }
        if (!StringUtils.hasText(clearText)) {
            throw new IllegalArgumentException("Le jeton Outlook Calendar est vide.");
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(clearText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return VERSION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Impossible de chiffrer le jeton Outlook Calendar.", exception);
        }
    }

    public String decrypt(String encryptedText) {
        if (!isConfigured()) {
            throw new IllegalStateException("La clé de chiffrement Outlook Calendar n'est pas configurée.");
        }
        if (!StringUtils.hasText(encryptedText) || !encryptedText.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("Le jeton Outlook Calendar chiffré est invalide.");
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(encryptedText.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Le jeton Outlook Calendar chiffré est invalide.");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Impossible de déchiffrer le jeton Outlook Calendar.", exception);
        }
    }

    private byte[] decodeKey(String encodedKey) {
        if (!StringUtils.hasText(encodedKey)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
