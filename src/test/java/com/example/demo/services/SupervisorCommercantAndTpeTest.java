package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.demo.dto.SupervisorTpeAssignRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifie la desactivation de compte commercant par le superviseur (dont le
 * cas d'un compte deja desactive) et le controle de permission granulaire
 * "peutAffecterTpe" pour un back-office qui tente d'affecter un TPE.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorCommercantAndTpeTest {

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

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
    void supervisorCanDeactivateActiveCommercant() {
        utilisateur superviseur = persistUser("superviseur.deactivate@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur commercantUser = persistUser("commercant.adeactiver@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant = commercantRepository.save(commercant);

        supervisorManagementService.deactivateCommercant(
            "Bearer " + tokenFor(superviseur),
            commercant.getIdCommercant()
        );

        utilisateur reloaded = utilisateurRepository.findById(commercantUser.getId()).orElseThrow();
        assertThat(reloaded.getActive()).isFalse();
    }

    @Test
    void cannotDeactivateAlreadyDeactivatedCommercant() {
        utilisateur superviseur = persistUser("superviseur.deactivate2@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur commercantUser = persistUser("commercant.dejainactif@test.lanacash.ma", RoleUser.COMMERCANT);
        commercantUser.setActive(false);
        commercantUser.setDateDesactivation(LocalDate.now());
        utilisateurRepository.save(commercantUser);
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant = commercantRepository.save(commercant);
        final Long commercantId = commercant.getIdCommercant();

        assertThatThrownBy(() ->
            supervisorManagementService.deactivateCommercant("Bearer " + tokenFor(superviseur), commercantId)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void backOfficeWithoutPermissionFlagStillReachesTpeLookup() {
        // La restriction par permission individuelle (peutAffecterTpe) a ete supprimee :
        // tout agent BACK_OFFICE peut affecter des references TPE. La requete echoue ici
        // seulement parce que la reference TPE fictive n'existe pas cote switch monetique.
        utilisateur backOfficeUser = persistUser("backoffice.sanstpe@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutAffecterTpe(false);
        backOfficeRepository.save(backOffice);

        String tpeId = "TPE-PERM-TEST-1";

        assertThatThrownBy(() ->
            supervisorManagementService.assignTpeToCommercant(
                "Bearer " + tokenFor(backOfficeUser),
                tpeId,
                new SupervisorTpeAssignRequest(1L)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Référence TPE introuvable");
    }

    @Test
    void rejectsAssignmentOfInactiveTpeEvenWithPermission() {
        utilisateur backOfficeUser = persistUser("backoffice.avectpe@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutAffecterTpe(true);
        backOfficeRepository.save(backOffice);

        String tpeId = "TPE-INACTIF-TEST-1";
        when(switchMonetiqueClient.parId(tpeId)).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, null, null, "TPE", "4G", false, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        assertThatThrownBy(() ->
            supervisorManagementService.assignTpeToCommercant(
                "Bearer " + tokenFor(backOfficeUser),
                tpeId,
                new SupervisorTpeAssignRequest(1L)
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
