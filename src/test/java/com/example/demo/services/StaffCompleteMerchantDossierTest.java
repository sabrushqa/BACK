package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.AffiliationActivationRequest;
import com.example.demo.dto.MerchantPdvProductRequest;
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
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exerce le chemin heureux de completion d'un dossier par une commerciale
 * (generation du compte-rendu, transition de statut, transmission au back
 * office) ainsi que le rejet quand le dossier n'est pas assigne au bon
 * commercial.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffCompleteMerchantDossierTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

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

    private AffiliationActivationRequest tpeActivationRequest() {
        return new AffiliationActivationRequest(
            "5411", "1.5%", "2.5%", "500", "1000", "200", "Standard",
            null, null, null, null, null, null, null,
            true, true, true, true, true, true, true, true,
            "NON_AFFILIE", null, "PROSPECTION", "Salon professionnel",
            "Jane Doe", "Gerante", "BTQ01", "Boutique Test", "Actionnaires Test",
            "Boutique Test SARL", null, "01/01/2020", "5", "Commerce general",
            "5411", "Standard", "Vente de produits", "50m2", "Proprietaire",
            "500000", "60%", "40%", "Fiable", "Bonne tenue", "RAS",
            "Casablanca", "01/01/2026"
        );
    }

    @Test
    void completesAssignedDossierAndTransitionsToBackOfficeValidation() {
        utilisateur commercialUser = persistUser("commercial.complete@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.complete@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        merchantUser.setActive(false);
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        utilisateur backOfficeUser = persistUser("backoffice.complete@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setNom("Saidi");
        backOffice.setPrenom("Hicham");
        backOfficeRepository.save(backOffice);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setCommercialeAssignee(commerciale);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.completeMerchantDossier(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            tpeActivationRequest()
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        assertThat(reloaded.getCommercialReportPath()).isNotBlank();
        assertThat(notificationsRepository.findAll())
            .anyMatch(notification -> notification.getUtilisateur().getId().equals(backOfficeUser.getId()));
    }

    private AffiliationActivationRequest ecommerceActivationRequest() {
        return new AffiliationActivationRequest(
            "5411", null, null, null, null, null, null,
            "1.5%", "2.5%", "300",
            null, null, null, null,
            true, true, true, true, true, true, true, true,
            "NON_AFFILIE", null, "PROSPECTION", "Salon professionnel",
            "Jane Doe", "Gerante", "BTQ01", "Boutique Test", "Actionnaires Test",
            "Boutique Test SARL", null, "01/01/2020", "5", "Commerce general",
            "5411", "Standard", "Vente de produits", "50m2", "Proprietaire",
            "500000", "60%", "40%", "Fiable", "Bonne tenue", "RAS",
            "Casablanca", "01/01/2026"
        );
    }

    private AffiliationActivationRequest combinedActivationRequest() {
        return new AffiliationActivationRequest(
            "5411", "1.4%", "2.4%", "500", "1000", "200", "Standard",
            "1.5%", "2.5%", "300",
            null, null, null, null,
            true, true, true, true, true, true, true, true,
            "NON_AFFILIE", null, "PROSPECTION", "Salon professionnel",
            "Jane Doe", "Gerante", "BTQ01", "Boutique Test", "Actionnaires Test",
            "Boutique Test SARL", null, "01/01/2020", "5", "Commerce general",
            "5411", "Standard", "Vente de produits", "50m2", "Proprietaire",
            "500000", "60%", "40%", "Fiable", "Bonne tenue", "RAS",
            "Casablanca", "01/01/2026"
        );
    }

    private AffiliationActivationRequest qrSoftposActivationRequest() {
        return new AffiliationActivationRequest(
            "5411", null, null, null, null, null, "Standard",
            null, null, null,
            "1%", "2%", "150", "Standard",
            true, true, true, true, true, true, true, true,
            "NON_AFFILIE", null, "PROSPECTION", "Salon professionnel",
            "Jane Doe", "Gerante", "BTQ01", "Boutique Test", "Actionnaires Test",
            "Boutique Test SARL", null, "01/01/2020", "5", "Commerce general",
            "5411", "Standard", "Vente de produits", "50m2", "Proprietaire",
            "500000", "60%", "40%", "Fiable", "Bonne tenue", "RAS",
            "Casablanca", "01/01/2026"
        );
    }

    @Test
    void completesEcommerceDossierWithEcommerceCommissionFields() {
        utilisateur commercialUser = persistUser("commercial.complete.ecommerce@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.complete.ecommerce@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        merchantUser.setActive(false);
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        dossier.setCommercialeAssignee(commerciale);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.completeMerchantDossier(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            ecommerceActivationRequest()
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        assertThat(reloaded.getCommissionLocaleEcommerce()).isEqualTo("1.5%");
    }

    @Test
    void completesCombinedDossierWithTpeAndEcommerceCommissionFields() {
        utilisateur commercialUser = persistUser("commercial.complete.combined@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.complete.combined@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        merchantUser.setActive(false);
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setTypeAffiliation(TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE);
        dossier.setCommercialeAssignee(commerciale);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.completeMerchantDossier(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            combinedActivationRequest()
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        assertThat(reloaded.getCommissionLocaleTpe()).isEqualTo("1.4%");
        assertThat(reloaded.getCommissionLocaleEcommerce()).isEqualTo("1.5%");
    }

    @Test
    void completesQrCodeDossierWithQrSoftposCommissionFields() {
        utilisateur commercialUser = persistUser("commercial.complete.qrcode@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.complete.qrcode@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        merchantUser.setActive(false);
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setTypeAffiliation(TypeAffiliation.QR_CODE);
        dossier.setCommercialeAssignee(commerciale);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.completeMerchantDossier(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            qrSoftposActivationRequest()
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        assertThat(reloaded.getCommissionLocaleQrSoftpos()).isEqualTo("1%");
        assertThat(reloaded.getConditionsQrSoftpos()).isEqualTo("Standard");
    }

    /**
     * Extension sur un PDV DEJA EXISTANT : pas de nouvelle visite/qualification
     * necessaire pour ce meme point de vente — le compte-rendu commercial du
     * dossier principal (deja genere) doit etre reutilise tel quel, pas
     * regenere.
     */
    @Test
    void reusesParentCommercialReportForExtensionOnExistingPdv() {
        utilisateur commercialUser = persistUser("commercial.ext-crc@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.ext-crc@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setNomPDV("Boutique historique CRC");
        pointVente.setCommercant(commercant);
        pointVente.setStatut("ACTIF");
        pointVente = pdvRepository.save(pointVente);

        // Dossier principal deja ACCEPTE, avec un compte-rendu deja genere
        // (simule le resultat d'un completeMerchantDossier anterieur).
        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setCommerciale(commerciale);
        principalDossier.setDateSoumission(LocalDate.now());
        principalDossier.setCommercialReportPath("/uploads/contracts/dossier-1/compte-rendu-commercial-1.pdf");
        principalDossier.setCommercialReportFileName("compte-rendu-commercial-1.pdf");
        principalDossier.setCommercialReportGeneratedAt(LocalDate.now().minusMonths(2));
        dossierAffiliationRepository.save(principalDossier);

        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                null, null,
                pointVente.getIdPDV()
            )
        );

        dossier_affiliation extensionDossier = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant())
            .stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier d'extension introuvable"));
        assertThat(extensionDossier.getRequestedPdvDejaExistant()).isTrue();

        staffAffiliationManagementService.completeMerchantDossier(
            "Bearer " + tokenFor(commercialUser),
            extensionDossier.getIdDossier(),
            tpeActivationRequest()
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(extensionDossier.getIdDossier()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        assertThat(reloaded.getCommercialReportPath()).isEqualTo(principalDossier.getCommercialReportPath());
        assertThat(reloaded.getCommercialReportFileName()).isEqualTo(principalDossier.getCommercialReportFileName());
        assertThat(reloaded.getCommercialReportGeneratedAt()).isEqualTo(principalDossier.getCommercialReportGeneratedAt());
    }

    /**
     * Symetrique : une extension sur un NOUVEAU point de vente (pas
     * existingPdvId) garde son propre compte-rendu, genere normalement —
     * seule l'extension sur un PDV deja existant reutilise celui du parent.
     */
    @Test
    void generatesOwnCommercialReportForExtensionOnNewPdv() {
        utilisateur commercialUser = persistUser("commercial.ext-crc-new@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.ext-crc-new@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setCommerciale(commerciale);
        principalDossier.setDateSoumission(LocalDate.now());
        principalDossier.setCommercialReportPath("/uploads/contracts/dossier-2/compte-rendu-commercial-2.pdf");
        dossierAffiliationRepository.save(principalDossier);

        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "Nouvelle boutique", "45 avenue Test", "Rabat", null, null, "0600000001", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                null, null,
                null
            )
        );

        dossier_affiliation extensionDossier = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant())
            .stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier d'extension introuvable"));
        assertThat(extensionDossier.getRequestedPdvDejaExistant()).isFalse();

        staffAffiliationManagementService.completeMerchantDossier(
            "Bearer " + tokenFor(commercialUser),
            extensionDossier.getIdDossier(),
            tpeActivationRequest()
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(extensionDossier.getIdDossier()).orElseThrow();
        assertThat(reloaded.getCommercialReportPath()).isNotBlank();
        assertThat(reloaded.getCommercialReportPath()).isNotEqualTo(principalDossier.getCommercialReportPath());
    }

    @Test
    void rejectsCompletionByCommercialNotAssignedToDossier() {
        utilisateur commercialAUser = persistUser("commercial.complete.a@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commercialeA = new commerciale();
        commercialeA.setUtilisateur(commercialAUser);
        commercialeRepository.save(commercialeA);

        utilisateur commercialBUser = persistUser("commercial.complete.b@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commercialeB = new commerciale();
        commercialeB.setUtilisateur(commercialBUser);
        commercialeB = commercialeRepository.save(commercialeB);

        utilisateur merchantUser = persistUser("commercant.complete2@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        merchantUser.setActive(false);
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setCommercialeAssignee(commercialeB);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        assertThatThrownBy(() ->
            staffAffiliationManagementService.completeMerchantDossier(
                "Bearer " + tokenFor(commercialAUser),
                dossierId,
                tpeActivationRequest()
            )
        ).isInstanceOf(ResponseStatusException.class);
    }
}
