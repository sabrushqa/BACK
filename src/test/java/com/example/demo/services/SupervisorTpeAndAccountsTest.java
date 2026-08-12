package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.CreateCommercialeRequest;
import com.example.demo.dto.SupervisorPasswordChangeRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exerce le reste des operations de gestion superviseur: stock TPE du switch,
 * activation, desactivation), creation de commerciale, gestion des comptes
 * back-office (desactivation, renvoi d'activation), changement de mot de
 * passe (echoue proprement car Keycloak est desactive en test).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorTpeAndAccountsTest {

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

    private utilisateur persistUser(String email, RoleUser role) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void getTpeStockReturnsReferencesProvidedBySwitch() {
        utilisateur superviseur = persistUser("superviseur.seed@test.lanacash.ma", RoleUser.SUPERVISEUR);
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            switchTpe("TPE-STOCK-001", "TPE", true),
            switchTpe("SOFTPOS-STOCK-001", "SOFTPOS", true),
            switchTpe("QRCODE-STOCK-001", "QR_CODE", true)
        ));

        var response = supervisorManagementService.getTpeStock("Bearer " + tokenFor(superviseur));

        assertThat(response.tpes()).extracting(item -> item.id())
            .containsExactly("TPE-STOCK-001", "SOFTPOS-STOCK-001", "QRCODE-STOCK-001");
    }

    @Test
    void activateAndDeactivateTpeUpdatesStatus() {
        utilisateur superviseur = persistUser("superviseur.tpe.status@test.lanacash.ma", RoleUser.SUPERVISEUR);
        String tpeId = "TPE-STATUS-TEST-1";
        when(switchMonetiqueClient.activer(tpeId)).thenReturn(switchTpe(tpeId, "TPE", true));
        when(switchMonetiqueClient.desactiver(tpeId)).thenReturn(switchTpe(tpeId, "TPE", false));

        supervisorManagementService.activateTpe("Bearer " + tokenFor(superviseur), tpeId);
        supervisorManagementService.deactivateTpe("Bearer " + tokenFor(superviseur), tpeId);

        verify(switchMonetiqueClient).activer(tpeId);
        verify(switchMonetiqueClient).desactiver(tpeId);
    }

    private SwitchMonetiqueClient.SwitchTpe switchTpe(String id, String nature, boolean active) {
        return new SwitchMonetiqueClient.SwitchTpe(
            id, null, null, nature, "4G", active, BigDecimal.ZERO, LocalDateTime.now()
        );
    }

    @Test
    void createCommercialePreparesAccountThenFailsGracefullyOnKeycloakStep() {
        utilisateur superviseur = persistUser("superviseur.createcommerciale@test.lanacash.ma", RoleUser.SUPERVISEUR);

        assertThatThrownBy(() ->
            supervisorManagementService.createCommerciale(
                "Bearer " + tokenFor(superviseur),
                new CreateCommercialeRequest(
                    "Fassi", "Nadia", "com.nouvelle@test.lanacash.ma", "COM-001", "Casablanca-Settat", "0600000001"
                )
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Le compte utilisateur est neanmoins cree en base avant l'echec Keycloak.
        assertThat(utilisateurRepository.existsByEmailIgnoreCase("com.nouvelle@test.lanacash.ma")).isTrue();
    }

    @Test
    void deactivatesBackOfficeAccount() {
        utilisateur superviseur = persistUser("superviseur.deactivatebo@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur backOfficeUser = persistUser("backoffice.adeactiver@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice = backOfficeRepository.save(backOffice);

        supervisorManagementService.deactivateBackOffice(
            "Bearer " + tokenFor(superviseur),
            backOffice.getIdBackOffice()
        );

        assertThat(utilisateurRepository.findById(backOfficeUser.getId()).orElseThrow().getActive()).isFalse();
    }

    @Test
    void sendBackOfficeActivationFailsGracefullyWhenKeycloakUnavailable() {
        utilisateur superviseur = persistUser("superviseur.sendactivation@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur backOfficeUser = persistUser("backoffice.areactiver@test.lanacash.ma", RoleUser.BACK_OFFICE);
        backOfficeUser.setActive(false);
        utilisateurRepository.save(backOfficeUser);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice = backOfficeRepository.save(backOffice);
        final Long backOfficeId = backOffice.getIdBackOffice();

        assertThatThrownBy(() ->
            supervisorManagementService.sendBackOfficeActivation(
                "Bearer " + tokenFor(superviseur),
                backOfficeId
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void changePasswordFailsGracefullyWhenKeycloakDisabled() {
        utilisateur superviseur = persistUser("superviseur.changepwd@test.lanacash.ma", RoleUser.SUPERVISEUR);

        assertThatThrownBy(() ->
            supervisorManagementService.changePassword(
                "Bearer " + tokenFor(superviseur),
                new SupervisorPasswordChangeRequest("AncienPass1!", "NouveauPass1!", "NouveauPass1!")
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
