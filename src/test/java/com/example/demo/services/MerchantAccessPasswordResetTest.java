package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.PasswordResetRequest;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documente le comportement actuel de requestPasswordReset face a des
 * comptes inexistants ou desactives. Note: les messages d'erreur distincts
 * ("aucun compte associe" vs "compte desactive") permettent en theorie a un
 * attaquant d'enumerer les e-mails valides via cet endpoint public - un
 * compromis UX/securite a arbitrer cote produit, pas une regression de ce test.
 */
@SpringBootTest
@Transactional
class MerchantAccessPasswordResetTest {

    @Autowired
    private MerchantAccessService merchantAccessService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Test
    void rejectsPasswordResetForUnknownEmail() {
        assertThatThrownBy(() ->
            merchantAccessService.requestPasswordReset(
                new PasswordResetRequest("inconnu.reset@test.lanacash.ma")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Aucun compte actif");
    }

    @Test
    void rejectsPasswordResetForDeactivatedAccount() {
        utilisateur user = new utilisateur();
        user.setEmail("desactive.reset@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(false);
        user.setDateDesactivation(LocalDate.now());
        user.setDateCreation(LocalDate.now());
        utilisateurRepository.save(user);

        assertThatThrownBy(() ->
            merchantAccessService.requestPasswordReset(
                new PasswordResetRequest("desactive.reset@test.lanacash.ma")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("desactive");
    }

    @Test
    void rejectsBlankEmail() {
        assertThatThrownBy(() ->
            merchantAccessService.requestPasswordReset(new PasswordResetRequest("  "))
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsResetForAccountPendingCommercialProcessing() {
        utilisateur user = new utilisateur();
        user.setEmail("attente.commercial.reset@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(false);
        user.setPasswordExpiresAt(null);
        user.setDateCreation(LocalDate.now());
        utilisateurRepository.save(user);

        assertThatThrownBy(() ->
            merchantAccessService.requestPasswordReset(
                new PasswordResetRequest("attente.commercial.reset@test.lanacash.ma")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("traitement commercial");
    }

    @Test
    void rejectsResetForAccountNotYetActivated() {
        utilisateur user = new utilisateur();
        user.setEmail("pasencoreactif.reset@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(false);
        user.setPasswordExpiresAt(LocalDateTime.now().plusMinutes(30));
        user.setDateCreation(LocalDate.now());
        utilisateurRepository.save(user);

        assertThatThrownBy(() ->
            merchantAccessService.requestPasswordReset(
                new PasswordResetRequest("pasencoreactif.reset@test.lanacash.ma")
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pas encore actif");
    }
}
