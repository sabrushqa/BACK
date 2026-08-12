package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.AssignAffiliationRequest;
import com.example.demo.dto.SupervisorTpeAssignRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce l'assignation d'un dossier d'auto-affiliation a une commerciale par
 * le superviseur, dont la regle metier de correspondance regionale.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorAssignmentTest {

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

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
    void assignsDossierToCommercialeInSameRegion() {
        utilisateur superviseur = persistUser("superviseur.assign@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Assign Test");
        commercant.setRegion("Casablanca-Settat");
        commercant = commercantRepository.save(commercant);

        utilisateur commercialUser = persistUser("commercial.assign@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setRegion("Casablanca-Settat");
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_ASSIGNATION);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();
        final Long commercialeId = commerciale.getIdCommercial();

        supervisorManagementService.assignAffiliationToCommerciale(
            "Bearer " + tokenFor(superviseur),
            dossierId,
            new AssignAffiliationRequest(commercialeId)
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.SOUMIS);
        assertThat(reloaded.getCommercialeAssignee().getIdCommercial()).isEqualTo(commercialeId);
    }

    @Test
    void rejectsAssignmentWhenCommercialeIsInDifferentRegion() {
        utilisateur superviseur = persistUser("superviseur.assign2@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Assign Test 2");
        commercant.setRegion("Casablanca-Settat");
        commercant = commercantRepository.save(commercant);

        utilisateur commercialUser = persistUser("commercial.assign2@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setRegion("Marrakech-Safi");
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_ASSIGNATION);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();
        final Long commercialeId = commerciale.getIdCommercial();

        assertThatThrownBy(() ->
            supervisorManagementService.assignAffiliationToCommerciale(
                "Bearer " + tokenFor(superviseur),
                dossierId,
                new AssignAffiliationRequest(commercialeId)
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignsTpeToCommercantWithValidatedDossier() {
        utilisateur backOfficeUser = persistUser("backoffice.assigntpe@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutAffecterTpe(true);
        backOfficeRepository.save(backOffice);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique TPE Assign Test");
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setNombreTpe(1);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        String tpeId = "TPE-SUPERVISOR-ASSIGN-1";
        String merchantId = commercant.getIdCommercant().toString();
        String pointOfSaleId = pointVente.getIdPDV().toString();
        SwitchMonetiqueClient.SwitchTpe terminal = switchTpe(tpeId, "TPE", true, null);
        when(switchMonetiqueClient.parId(tpeId)).thenReturn(Optional.of(terminal));
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());
        when(switchMonetiqueClient.affecter(
            org.mockito.ArgumentMatchers.eq(tpeId),
            org.mockito.ArgumentMatchers.eq(merchantId),
            org.mockito.ArgumentMatchers.eq(pointOfSaleId),
            any(), any(), any()
        )).thenReturn(switchTpe(tpeId, "TPE", true, merchantId));

        supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            tpeId,
            new SupervisorTpeAssignRequest(dossierId)
        );

        verify(switchMonetiqueClient).affecter(
            org.mockito.ArgumentMatchers.eq(tpeId),
            org.mockito.ArgumentMatchers.eq(merchantId),
            org.mockito.ArgumentMatchers.eq(pointOfSaleId),
            any(), any(), any()
        );
    }

    @Test
    void getEligibleTpesForDossierReturnsOnlyMatchingUnassignedTerminals() {
        utilisateur backOfficeUser = persistUser("backoffice.eligible@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutAffecterTpe(true);
        backOfficeRepository.save(backOffice);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Eligible Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        when(switchMonetiqueClient.stockDisponible("TPE")).thenReturn(List.of(
            switchTpe("TPE-ELIGIBLE-1", "TPE", true, null)
        ));

        var response = supervisorManagementService.getEligibleTpesForDossier(
            "Bearer " + tokenFor(backOfficeUser),
            dossierId
        );

        assertThat(response.tpes()).hasSize(1);
    }

    private SwitchMonetiqueClient.SwitchTpe switchTpe(
        String id,
        String nature,
        boolean active,
        String commercantId
    ) {
        return new SwitchMonetiqueClient.SwitchTpe(
            id, commercantId, null, nature, "4G", active, BigDecimal.ZERO, LocalDateTime.now()
        );
    }
}
