package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.AffiliationReviewRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifie que la validation d'un dossier par le back-office respecte la
 * permission peutValiderDossiers et le statut attendu du dossier
 * (EN_ATTENTE_VALIDATION_BOA uniquement).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffReviewDossierTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

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

    private dossier_affiliation newDossier(StatusDossier status) {
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Review Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(status);
        dossier.setDateSoumission(LocalDate.now());
        return dossierAffiliationRepository.save(dossier);
    }

    @Test
    void reviewFromBackOfficeWithoutPermissionFlagReachesReviewLogic() {
        // La restriction par permission individuelle (peutValiderDossiers) a ete supprimee :
        // tout agent BACK_OFFICE atteint la logique metier de revue. L'appel echoue ici
        // seulement parce que le commercant du dossier de test n'a pas de compte utilisateur
        // rattache (donnee manquante), pas a cause d'un refus de permission.
        utilisateur backOfficeUser = persistUser("bo.sansvalidation@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(false);
        backOfficeRepository.save(backOffice);

        dossier_affiliation dossier = newDossier(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        final Long dossierId = dossier.getIdDossier();

        assertThatThrownBy(() ->
            staffAffiliationManagementService.reviewMerchantDossier(
                "Bearer " + tokenFor(backOfficeUser),
                dossierId,
                new AffiliationReviewRequest("ACCEPTE", null)
            )
        )
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReviewWhenDossierNotAwaitingValidation() {
        utilisateur backOfficeUser = persistUser("bo.avecvalidation@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);

        dossier_affiliation dossier = newDossier(StatusDossier.BROUILLON);
        final Long dossierId = dossier.getIdDossier();

        assertThatThrownBy(() ->
            staffAffiliationManagementService.reviewMerchantDossier(
                "Bearer " + tokenFor(backOfficeUser),
                dossierId,
                new AffiliationReviewRequest("ACCEPTE", null)
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingDecision() {
        utilisateur backOfficeUser = persistUser("bo.decisionmanquante@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);

        dossier_affiliation dossier = newDossier(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        final Long dossierId = dossier.getIdDossier();

        assertThatThrownBy(() ->
            staffAffiliationManagementService.reviewMerchantDossier(
                "Bearer " + tokenFor(backOfficeUser),
                dossierId,
                new AffiliationReviewRequest("", null)
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNewPdvDossierAndGeneratesContractWithoutKeycloakProvisioning() {
        utilisateur backOfficeUser = persistUser("bo.acceptnewpdv@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);

        utilisateur merchantUser = persistUser("merchant.acceptnewpdv@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Accept NewPdv Test");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setOrigineCreation("NOUVEAU_PDV");
        dossier.setBackOffice(backOffice);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        var response = staffAffiliationManagementService.reviewMerchantDossier(
            "Bearer " + tokenFor(backOfficeUser),
            dossierId,
            new AffiliationReviewRequest("ACCEPTE", null)
        );

        assertThat(response.message()).contains("contrat");
        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.CONTRAT_A_SIGNER);
        assertThat(reloaded.getGeneratedContractPath()).isNotBlank();
    }

    @Test
    void acceptingRegularDossierFailsWhenKeycloakProvisioningIsDisabled() {
        utilisateur backOfficeUser = persistUser("bo.acceptregular@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);

        utilisateur merchantUser = persistUser("merchant.acceptregular@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Accept Regular Test");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        assertThatThrownBy(() ->
            staffAffiliationManagementService.reviewMerchantDossier(
                "Bearer " + tokenFor(backOfficeUser),
                dossierId,
                new AffiliationReviewRequest("ACCEPTE", null)
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void sendsDossierBackToCommercialWithMotif() {
        utilisateur backOfficeUser = persistUser("bo.refuse@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);

        utilisateur merchantUser = persistUser("merchant.refuse@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Refuse Test");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        var response = staffAffiliationManagementService.reviewMerchantDossier(
            "Bearer " + tokenFor(backOfficeUser),
            dossierId,
            new AffiliationReviewRequest(
                "REFUSE",
                "Types de problème: DOCUMENTS\nMotif: Documents illisibles"
            )
        );

        assertThat(response.message()).contains("renvoyé");
        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.INCOMPLET);
        assertThat(reloaded.getMotifRefus()).contains("Motif: Documents illisibles");
    }

    @Test
    void sendsDossierBackToAssignedCommercialWithNotificationAndEmail() {
        utilisateur backOfficeUser = persistUser("bo.refuse.avec.commercial@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);

        utilisateur commercialUser = persistUser("commercial.refuse.notif@test.lanacash.ma", RoleUser.COMMERCIAL);
        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setNom("Idrissi");
        commerciale.setPrenom("Kenza");
        commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("merchant.refuse.notif@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Refuse Notif Test");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setCommerciale(commerciale);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.reviewMerchantDossier(
            "Bearer " + tokenFor(backOfficeUser),
            dossierId,
            new AffiliationReviewRequest(
                "REFUSE",
                "Types de problème: DOCUMENTS\nMotif: Documents illisibles"
            )
        );

        assertThat(notificationsRepository.findAll())
            .anyMatch(notification -> notification.getUtilisateur().getId().equals(commercialUser.getId()));
    }

    @Autowired
    private com.example.demo.repositories.CommercialeRepository commercialeRepository;

    @Autowired
    private com.example.demo.repositories.NotificationsRepository notificationsRepository;

    @Test
    void rejectsSendingBackWithoutMotif() {
        utilisateur backOfficeUser = persistUser("bo.refusesansmotif@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);

        dossier_affiliation dossier = newDossier(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        final Long dossierId = dossier.getIdDossier();

        assertThatThrownBy(() ->
            staffAffiliationManagementService.reviewMerchantDossier(
                "Bearer " + tokenFor(backOfficeUser),
                dossierId,
                new AffiliationReviewRequest("REFUSE", "")
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
