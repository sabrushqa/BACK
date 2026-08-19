package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.dto.MerchantPdvProductRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeNotification;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.SousCommercantRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exerce les branches de rejet de requestNewPdvProduct (role/statut du
 * compte, requete nulle, champs e-commerce obligatoires) et le chemin de
 * geocodage automatique (aucune coordonnee manuelle fournie), jamais exerce
 * ailleurs car les autres tests fournissent toujours latitude/longitude
 * manuelles pour eviter tout vrai appel reseau Nominatim.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantWorkspacePdvProductValidationTest {

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private SousCommercantRepository sousCommercantRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private SwitchMonetiqueClient switchMonetiqueClient;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private ActivationMailService activationMailService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Autowired
    private SupervisorNotificationService supervisorNotificationService;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

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

    private commercant persistAcceptedTpeMerchant(String email) {
        utilisateur merchantUser = persistUser(email, RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);
        return commercant;
    }

    @Test
    void rejectsExpiredToken() {
        commercant commercant = persistAcceptedTpeMerchant("commercant.newpdv.expired@test.lanacash.ma");
        String expiredToken = TestJwtSupport.mintExpiredToken(
            "kc-sub-" + commercant.getUtilisateur().getId(), commercant.getUtilisateur().getEmail()
        );

        assertThatThrownBy(() -> merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + expiredToken,
            new MerchantPdvProductRequest(
                "Nouveau PDV", "12 rue Test", "Casablanca", null, null, "0600000000", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                33.5731, -7.5898,
                null
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsSessionInvalidatedAfterTokenIssuance() throws InterruptedException {
        commercant commercant = persistAcceptedTpeMerchant("commercant.newpdv.invalidated@test.lanacash.ma");
        utilisateur merchantUser = commercant.getUtilisateur();
        String token = tokenFor(merchantUser);

        Thread.sleep(1100);
        merchantUser.setTokenVersion(1);
        utilisateurRepository.save(merchantUser);

        assertThatThrownBy(() -> merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + token,
            new MerchantPdvProductRequest(
                "Nouveau PDV", "12 rue Test", "Casablanca", null, null, "0600000000", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                33.5731, -7.5898,
                null
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED)
            .hasMessageContaining("invalidee");
    }

    @Test
    void rejectsNonMerchantRole() {
        utilisateur supervisorUser = persistUser("superviseur.newpdv.role@test.lanacash.ma", RoleUser.SUPERVISEUR);

        assertThatThrownBy(() -> merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(supervisorUser),
            new MerchantPdvProductRequest(
                "Nouveau PDV", "12 rue Test", "Casablanca", null, null, "0600000000", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                33.5731, -7.5898,
                null
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("commerçant");
    }

    @Test
    void rejectsInactiveMerchantAccount() {
        utilisateur merchantUser = persistUser("commercant.newpdv.inactive@test.lanacash.ma", RoleUser.COMMERCANT);
        merchantUser.setActive(false);
        merchantUser.setDateDesactivation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercantRepository.save(commercant);

        assertThatThrownBy(() -> merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "Nouveau PDV", "12 rue Test", "Casablanca", null, null, "0600000000", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                33.5731, -7.5898,
                null
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("actif");
    }

    @Test
    void rejectsNullRequestBody() {
        commercant commercant = persistAcceptedTpeMerchant("commercant.newpdv.nullrequest@test.lanacash.ma");

        assertThatThrownBy(() -> merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(commercant.getUtilisateur()),
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("informations de la demande");
    }

    @Test
    void rejectsEcommerceRequestMissingServiceMode() {
        utilisateur merchantUser = persistUser("commercant.newpdv.ecomode@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        assertThatThrownBy(() -> merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "E_COMMERCE", null, null, null, null, null, null,
                null, "https://nouvelle-boutique.example.ma", null,
                null, null,
                null
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mode de service e-commerce");
    }

    @Test
    void rejectsEcommerceRequestMissingUrlAndApplication() {
        utilisateur merchantUser = persistUser("commercant.newpdv.ecourl@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        assertThatThrownBy(() -> merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "E_COMMERCE", null, null, null, null, null, null,
                "INTEGRATION_API", null, null,
                null, null,
                null
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("site marchand ou l'application mobile");
    }

    @Test
    void rejectsTpeRequestMissingRequiredPdvFields() {
        commercant commercant = persistAcceptedTpeMerchant("commercant.newpdv.missingfields@test.lanacash.ma");

        assertThatThrownBy(() -> merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(commercant.getUtilisateur()),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                null, null,
                null
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nom du point de vente");
    }

    @Test
    void geocodesPdvAutomaticallyWhenNoManualCoordinatesProvided() {
        GeocodingService geocodingService = mock(GeocodingService.class);
        when(geocodingService.geocoder(any(), any(), any(), any()))
            .thenReturn(Optional.of(new GeocodingService.Coordonnees(33.9, -6.9)));
        MerchantWorkspaceManagementService service = new MerchantWorkspaceManagementService(
            utilisateurRepository,
            commercantRepository,
            dossierAffiliationRepository,
            pdvRepository,
            sousCommercantRepository,
            tpeRepository,
            switchMonetiqueClient,
            passwordHashService,
            activationMailService,
            jwtService,
            keycloakAdminService,
            geocodingService,
            supervisorNotificationService,
            60
        );

        utilisateur merchantUser = persistUser("commercant.newpdv.autogeocode@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        service.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "Nouveau PDV Autogeocode", "5 avenue Test", "Fes", null, null, "0600000000", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                null, null,
                null
            )
        );

        pdv saved = pdvRepository.findAll().stream()
            .filter(point -> "Nouveau PDV Autogeocode".equals(point.getNomPDV()))
            .findFirst()
            .orElseThrow();
        assertThat(saved.getLatitude()).isEqualTo(33.9);
        assertThat(saved.getLongitude()).isEqualTo(-6.9);
    }

    @Test
    void notifiesTheCommercialAndBackOfficeAlreadyAssignedToThisMerchant() {
        utilisateur merchantUser = persistUser("commercant.newpdv.notify@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        utilisateur commercialUser = persistUser("commercial.newpdv.notify@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur boaUser = persistUser("boa.newpdv.notify@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(boaUser);
        backOffice = backOfficeRepository.save(backOffice);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setCommerciale(commerciale);
        acceptedDossier.setBackOffice(backOffice);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "Nouveau PDV Notify", "8 rue Test", "Rabat", null, null, "0600000000", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                33.5731, -7.5898,
                null
            )
        );

        // Meme commercial et meme BOA que le dossier principal (continuite
        // d'affectation), tous deux notifies de la nouvelle demande.
        assertThat(notificationsRepository.findAll())
            .anyMatch(n -> n.getUtilisateur().getId().equals(commercialUser.getId())
                && n.getTypeNotification() == TypeNotification.DOSSIER_ASSIGNE);
        assertThat(notificationsRepository.findAll())
            .anyMatch(n -> n.getUtilisateur().getId().equals(boaUser.getId())
                && n.getTypeNotification() == TypeNotification.DOSSIER_A_VALIDER_BOA);
    }
}
