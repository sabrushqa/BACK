package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests unitaires purs (sans DB, sans contexte Spring) du hachage PBKDF2 des
 * mots de passe et de la compatibilite avec l'ancien format en clair.
 */
class PasswordHashServiceTest {

    private final PasswordHashService passwordHashService = new PasswordHashService();

    @Test
    void hashProducesDifferentOutputEachTimeDueToRandomSalt() {
        String hash1 = passwordHashService.hash("MotDePasse123!");
        String hash2 = passwordHashService.hash("MotDePasse123!");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void matchesReturnsTrueForCorrectPassword() {
        String hashed = passwordHashService.hash("MotDePasse123!");

        assertThat(passwordHashService.matches("MotDePasse123!", hashed)).isTrue();
    }

    @Test
    void matchesReturnsFalseForIncorrectPassword() {
        String hashed = passwordHashService.hash("MotDePasse123!");

        assertThat(passwordHashService.matches("MauvaisMotDePasse", hashed)).isFalse();
    }

    @Test
    void matchesTrimsSurroundingWhitespaceOnRawPassword() {
        String hashed = passwordHashService.hash("MotDePasse123!");

        assertThat(passwordHashService.matches("  MotDePasse123!  ", hashed)).isTrue();
    }

    @Test
    void matchesFallsBackToPlainTextComparisonForLegacyStoredPasswords() {
        // Ancien format non hache (avant migration PBKDF2): comparaison directe.
        assertThat(passwordHashService.matches("ancienMotDePasse", "ancienMotDePasse")).isTrue();
        assertThat(passwordHashService.matches("mauvais", "ancienMotDePasse")).isFalse();
    }

    @Test
    void isLegacyPlainTextFormatDetectsNonPbkdf2Values() {
        assertThat(passwordHashService.isLegacyPlainTextFormat("ancienMotDePasse")).isTrue();
        assertThat(passwordHashService.isLegacyPlainTextFormat(passwordHashService.hash("MotDePasse123!"))).isFalse();
        assertThat(passwordHashService.isLegacyPlainTextFormat(null)).isFalse();
        assertThat(passwordHashService.isLegacyPlainTextFormat("")).isFalse();
    }

    @Test
    void matchesReturnsFalseForMalformedStoredHash() {
        assertThat(passwordHashService.matches("password", "pbkdf2$notanumber$salt$hash")).isFalse();
    }

    @Test
    void matchesReturnsFalseForBlankInputs() {
        assertThat(passwordHashService.matches(null, "anything")).isFalse();
        assertThat(passwordHashService.matches("password", null)).isFalse();
        assertThat(passwordHashService.matches("", "")).isFalse();
    }

    @Test
    void hashRejectsBlankPassword() {
        assertThatThrownBy(() -> passwordHashService.hash("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchesReturnsFalseForStoredHashWithWrongPartCount() {
        assertThat(passwordHashService.matches("password", "pbkdf2$120000$onlythreeparts")).isFalse();
    }

    @Test
    void matchesReturnsFalseForInvalidBase64InStoredHash() {
        assertThat(
            passwordHashService.matches("password", "pbkdf2$120000$not-valid-base64!!$alsoinvalid!!")
        ).isFalse();
    }
}
