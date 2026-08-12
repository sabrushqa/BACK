package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.demo.dto.MerchantSessionResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.TpeRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie la nouvelle regle de blocage de l'espace commercant : un dossier
 * ACCEPTE ne suffit plus a debloquer le workspace si son type d'affiliation
 * implique un encaissement physique (TPE/SOFTPOS/QR_CODE/mixte) tant qu'aucun
 * TPE n'a ete reellement affecte par le BOA. Seul le E_COMMERCE pur reste
 * debloque des l'acceptation, sans TPE requis.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantAccessWorkspaceUnlockTest {

    @Autowired
    private MerchantAccessService merchantAccessService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

    private commercant persistMerchant(String email) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        utilisateurRepository.save(user);

        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        return commercantRepository.save(commercant);
    }

    private dossier_affiliation persistDossier(commercant commercant, TypeAffiliation type, StatusDossier status) {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(status);
        dossier.setTypeAffiliation(type);
        dossier.setDateSoumission(LocalDate.now());
        return dossierAffiliationRepository.save(dossier);
    }

    private String tokenFor(commercant commercant) {
        return TestJwtSupport.mintKeycloakToken(
            "kc-sub-" + commercant.getUtilisateur().getId(),
            commercant.getUtilisateur().getEmail(),
            300
        );
    }

    private MerchantSessionResponse session(commercant commercant) {
        return merchantAccessService.currentSession("Bearer " + tokenFor(commercant));
    }

    @Test
    void tpeDossierAcceptedWithoutAssignedTpeStaysLocked() {
        commercant commercant = persistMerchant("commercant.lock.tpe.noassign@test.lanacash.ma");
        persistDossier(commercant, TypeAffiliation.TPE, StatusDossier.ACCEPTE);

        MerchantSessionResponse response = session(commercant);

        assertThat(response.workspaceUnlocked()).isFalse();
        assertThat(response.dossierStatus()).isEqualTo("ACCEPTE");
    }

    @Test
    void tpeDossierAcceptedWithAssignedTpeUnlocks() {
        commercant commercant = persistMerchant("commercant.unlock.tpe.assigned@test.lanacash.ma");
        persistDossier(commercant, TypeAffiliation.TPE, StatusDossier.ACCEPTE);

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV Test");
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        tpe terminal = new tpe();
        terminal.setNumeroSerie("TPE-TEST-0001");
        terminal.setPdv(pointVente);
        tpeRepository.save(terminal);

        MerchantSessionResponse response = session(commercant);

        assertThat(response.workspaceUnlocked()).isTrue();
    }

    @Test
    void ecommerceDossierAcceptedUnlocksWithoutAnyTpe() {
        commercant commercant = persistMerchant("commercant.unlock.ecommerce@test.lanacash.ma");
        persistDossier(commercant, TypeAffiliation.E_COMMERCE, StatusDossier.ACCEPTE);

        MerchantSessionResponse response = session(commercant);

        assertThat(response.workspaceUnlocked()).isTrue();
    }

    @Test
    void softposDossierAcceptedWithoutAssignedTpeStaysLocked() {
        commercant commercant = persistMerchant("commercant.lock.softpos@test.lanacash.ma");
        persistDossier(commercant, TypeAffiliation.SOFTPOS, StatusDossier.ACCEPTE);

        MerchantSessionResponse response = session(commercant);

        assertThat(response.workspaceUnlocked()).isFalse();
    }

    @Test
    void tpeDossierNotYetAcceptedStaysLockedRegardlessOfTpeAssignment() {
        commercant commercant = persistMerchant("commercant.lock.notaccepted@test.lanacash.ma");
        persistDossier(commercant, TypeAffiliation.TPE, StatusDossier.SOUMIS);

        MerchantSessionResponse response = session(commercant);

        assertThat(response.workspaceUnlocked()).isFalse();
    }

    /**
     * Reproduit le flux BOA reel (assignTpeToCommercant -> Oracle uniquement,
     * jamais la table locale "tpe") : sans la verification Oracle, ce test
     * echouait (workspace jamais debloque malgre l'affectation reelle).
     */
    @Test
    void tpeDossierAcceptedWithOracleOnlyAssignmentUnlocks() {
        commercant commercant = persistMerchant("commercant.unlock.oracle.only@test.lanacash.ma");
        persistDossier(commercant, TypeAffiliation.TPE, StatusDossier.ACCEPTE);
        String idCommercant = commercant.getIdCommercant().toString();

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-ORACLE-1", idCommercant, "PDV-1", "TPE", "4G", true,
                BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        MerchantSessionResponse response = session(commercant);

        assertThat(response.workspaceUnlocked()).isTrue();
    }

    @Test
    void staysLockedWithoutCrashingWhenSwitchMonetiqueServiceUnreachable() {
        commercant commercant = persistMerchant("commercant.lock.oracle.down@test.lanacash.ma");
        persistDossier(commercant, TypeAffiliation.TPE, StatusDossier.ACCEPTE);

        when(switchMonetiqueClient.stockComplet()).thenThrow(new IllegalStateException("indisponible"));

        MerchantSessionResponse response = session(commercant);

        assertThat(response.workspaceUnlocked()).isFalse();
    }
}
