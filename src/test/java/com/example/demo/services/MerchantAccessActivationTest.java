package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.ActivationAccountRequest;
import com.example.demo.dto.MerchantSessionResponse;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifie les regles de validation de l'activation de compte commercant:
 * mot de passe temporaire expire/incorrect, compte desactive, nouveau mot
 * de passe trop court. Keycloak est desactive en test (isReady()=false),
 * donc une activation par ailleurs valide echoue en SERVICE_UNAVAILABLE
 * plutot qu'en succes complet - ce test verifie les rejets en amont de cet
 * appel, qui sont ceux qui protegent reellement contre les abus.
 */
@SpringBootTest
@Transactional
class MerchantAccessActivationTest {

    @Autowired
    private MerchantAccessService merchantAccessService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    private utilisateur persistPendingActivationUser(
        String email,
        String temporaryPasswordHash,
        LocalDateTime expiresAt,
        boolean deactivated
    ) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(false);
        user.setPassword(temporaryPasswordHash);
        user.setPasswordExpiresAt(expiresAt);
        user.setDateCreation(LocalDate.now());
        if (deactivated) {
            user.setDateDesactivation(LocalDate.now());
        }
        return utilisateurRepository.save(user);
    }

    @Test
    void rejectsExpiredTemporaryPassword() {
        persistPendingActivationUser(
            "activation.expire@test.lanacash.ma",
            passwordHashService.hash("TempPass123!"),
            LocalDateTime.now().minusMinutes(1),
            false
        );

        assertThatThrownBy(() ->
            merchantAccessService.activateAccount(
                new ActivationAccountRequest("activation.expire@test.lanacash.ma", "TempPass123!", "NouveauPass123!")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expire");
    }

    @Test
    void rejectsWrongTemporaryPassword() {
        persistPendingActivationUser(
            "activation.mauvais.mdp@test.lanacash.ma",
            passwordHashService.hash("TempPass123!"),
            LocalDateTime.now().plusMinutes(30),
            false
        );

        assertThatThrownBy(() ->
            merchantAccessService.activateAccount(
                new ActivationAccountRequest(
                    "activation.mauvais.mdp@test.lanacash.ma",
                    "MauvaisMotDePasse",
                    "NouveauPass123!"
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalide");
    }

    @Test
    void rejectsActivationForDeactivatedAccount() {
        persistPendingActivationUser(
            "activation.desactive@test.lanacash.ma",
            passwordHashService.hash("TempPass123!"),
            LocalDateTime.now().plusMinutes(30),
            true
        );

        assertThatThrownBy(() ->
            merchantAccessService.activateAccount(
                new ActivationAccountRequest(
                    "activation.desactive@test.lanacash.ma",
                    "TempPass123!",
                    "NouveauPass123!"
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("desactive");
    }

    @Test
    void rejectsTooShortNewPassword() {
        persistPendingActivationUser(
            "activation.courtpass@test.lanacash.ma",
            passwordHashService.hash("TempPass123!"),
            LocalDateTime.now().plusMinutes(30),
            false
        );

        assertThatThrownBy(() ->
            merchantAccessService.activateAccount(
                new ActivationAccountRequest("activation.courtpass@test.lanacash.ma", "TempPass123!", "court")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("8 caracteres");
    }

    @Test
    void rejectsUnknownEmail() {
        assertThatThrownBy(() ->
            merchantAccessService.activateAccount(
                new ActivationAccountRequest("inconnu.activation@test.lanacash.ma", "TempPass123!", "NouveauPass123!")
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsActivationStillAwaitingCommercialProcessing() {
        persistPendingActivationUser(
            "activation.entraitement@test.lanacash.ma",
            passwordHashService.hash("TempPass123!"),
            null,
            false
        );

        assertThatThrownBy(() ->
            merchantAccessService.activateAccount(
                new ActivationAccountRequest("activation.entraitement@test.lanacash.ma", "TempPass123!", "NouveauPass123!")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("en cours de traitement");
    }

    @Test
    void rejectsTemporaryPasswordForKeycloakOnlyAccountWhenKeycloakDisabled() {
        // Compte sans mot de passe local (purement Keycloak): la validation
        // retombe sur keycloakAdminService.passwordMatches(), qui renvoie
        // toujours false quand Keycloak est desactive en test - branche
        // jamais exercee par les autres tests qui fixent tous un hash local.
        utilisateur user = new utilisateur();
        user.setEmail("activation.keycloakonly@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(false);
        user.setPassword(null);
        user.setPasswordExpiresAt(LocalDateTime.now().plusMinutes(30));
        user.setDateCreation(LocalDate.now());
        utilisateurRepository.save(user);

        assertThatThrownBy(() ->
            merchantAccessService.activateAccount(
                new ActivationAccountRequest(
                    "activation.keycloakonly@test.lanacash.ma", "TempPass123!", "NouveauPass123!"
                )
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalide");
    }

    @Test
    void validRequestFailsGracefullyWhenKeycloakUnavailable() {
        persistPendingActivationUser(
            "activation.valide@test.lanacash.ma",
            passwordHashService.hash("TempPass123!"),
            LocalDateTime.now().plusMinutes(30),
            false
        );

        assertThatThrownBy(() ->
            merchantAccessService.activateAccount(
                new ActivationAccountRequest("activation.valide@test.lanacash.ma", "TempPass123!", "NouveauPass123!")
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
