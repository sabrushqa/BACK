package com.example.demo.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PasswordHashService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordHashService.class);

    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String hash(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire.");
        }

        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword.trim().toCharArray(), salt, ITERATIONS, KEY_LENGTH);

        return PREFIX
            + "$"
            + ITERATIONS
            + "$"
            + Base64.getEncoder().encodeToString(salt)
            + "$"
            + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Indique si un mot de passe stocke est encore dans l'ancien format en clair
     * (avant migration PBKDF2). A utiliser par l'appelant apres un matches() reussi
     * pour re-hacher et persister immediatement le mot de passe (upgrade-on-login),
     * afin de reduire au minimum la fenetre pendant laquelle un mot de passe en
     * clair peut rester en base.
     */
    public boolean isLegacyPlainTextFormat(String storedPassword) {
        return StringUtils.hasText(storedPassword) && !storedPassword.startsWith(PREFIX + "$");
    }

    public boolean matches(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(storedPassword)) {
            return false;
        }

        String trimmedRawPassword = rawPassword.trim();

        if (isLegacyPlainTextFormat(storedPassword)) {
            LOGGER.warn(
                "Authentification via l'ancien format de mot de passe en clair : "
                    + "le compte doit etre migre vers PBKDF2 (upgrade-on-login attendu de l'appelant)."
            );
            return MessageDigest.isEqual(
                trimmedRawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8)
            );
        }

        String[] parts = storedPassword.split("\\$");
        if (parts.length != 4) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(
                trimmedRawPassword.toCharArray(),
                salt,
                iterations,
                expectedHash.length * 8
            );

            return MessageDigest.isEqual(actualHash, expectedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);

        try {
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA256"
            );
            return secretKeyFactory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Impossible de sécuriser le mot de passe.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
