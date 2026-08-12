package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Verifie que KeycloakAdminService court-circuite proprement (aucun appel
 * reseau, aucune exception) quand il n'est pas "ready": desactive
 * explicitement, ou identifiants admin manquants.
 */
class KeycloakAdminServiceTest {

    private KeycloakAdminService buildService(boolean enabled, String adminUsername, String adminPassword) {
        return new KeycloakAdminService(
            RestClient.builder(),
            enabled,
            "http://127.0.0.1:1",
            "PFE26",
            "portail-affiliation",
            "master",
            "admin-cli",
            adminUsername,
            adminPassword,
            false
        );
    }

    private utilisateur sampleUser() {
        utilisateur user = new utilisateur();
        user.setEmail("keycloak.test@lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        return user;
    }

    @Test
    void provisionUserReturnsFalseWhenDisabled() {
        KeycloakAdminService service = buildService(false, "admin", "admin-password");

        assertThat(service.provisionUser(sampleUser(), "TempPass123!")).isFalse();
    }

    @Test
    void provisionUserReturnsFalseWhenAdminCredentialsMissing() {
        KeycloakAdminService service = buildService(true, "", "");

        assertThat(service.provisionUser(sampleUser(), "TempPass123!")).isFalse();
    }

    @Test
    void sendPasswordSetupEmailReturnsFalseWhenDisabled() {
        KeycloakAdminService service = buildService(false, "admin", "admin-password");

        assertThat(service.sendPasswordSetupEmail(sampleUser(), "http://localhost:4200/login")).isFalse();
    }

    @Test
    void disableUserDoesNothingAndNeverThrowsWhenDisabled() {
        KeycloakAdminService service = buildService(false, "admin", "admin-password");

        assertThatCode(() -> service.disableUser(sampleUser())).doesNotThrowAnyException();
    }

    @Test
    void provisionUserReturnsFalseForNullEmail() {
        KeycloakAdminService service = buildService(true, "admin", "admin-password");
        utilisateur userWithoutEmail = new utilisateur();

        assertThat(service.provisionUser(userWithoutEmail, "TempPass123!")).isFalse();
    }

    @Test
    void setPermanentPasswordThrowsWhenDisabled() {
        KeycloakAdminService service = buildService(false, "admin", "admin-password");

        assertThatThrownBy(() -> service.setPermanentPassword(sampleUser(), "NouveauPass123!"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non configurée");
    }

    @Test
    void setPermanentPasswordThrowsWhenAdminCredentialsMissing() {
        KeycloakAdminService service = buildService(true, "", "");

        assertThatThrownBy(() -> service.setPermanentPassword(sampleUser(), "NouveauPass123!"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passwordMatchesReturnsFalseWhenDisabled() {
        KeycloakAdminService service = buildService(false, "admin", "admin-password");

        assertThat(service.passwordMatches(sampleUser(), "AnyPassword1!")).isFalse();
    }

    @Test
    void requireOtpForExistingUsersReturnsFalseWhenDisabled() {
        KeycloakAdminService service = buildService(false, "admin", "admin-password");

        assertThat(service.requireOtpForExistingUsers()).isFalse();
    }

    @Test
    void configureClientReturnsFalseWhenDisabled() {
        KeycloakAdminService service = buildService(false, "admin", "admin-password");

        assertThat(service.configureClient(List.of("http://localhost:4200/*"), List.of())).isFalse();
    }

    @Test
    void configureClientReturnsFalseWhenClientIdIsBlank() {
        KeycloakAdminService service = new KeycloakAdminService(
            RestClient.builder(),
            true,
            "http://127.0.0.1:1",
            "PFE26",
            "",
            "master",
            "admin-cli",
            "admin",
            "admin-password",
            false
        );

        assertThat(service.configureClient(List.of("http://localhost:4200/*"), List.of())).isFalse();
    }

    @Test
    void configureClientHandlesNullRedirectUrisAndWebOriginsWithoutThrowing() {
        KeycloakAdminService service = buildService(true, "admin", "admin-password");

        assertThat(service.configureClient(null, null)).isFalse();
    }

    @Test
    void configureRealmSmtpReturnsFalseWhenDisabled() {
        KeycloakAdminService service = buildService(false, "admin", "admin-password");

        boolean result = service.configureRealmSmtp(
            "smtp.gmail.com", "587", "no-reply@lanacash.ma", "Lana Cash",
            "", "", true, false, ""
        );

        assertThat(result).isFalse();
    }
}
