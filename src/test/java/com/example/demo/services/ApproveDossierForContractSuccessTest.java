package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.config.DocumentMimeValidator;
import com.example.demo.dto.AffiliationReviewRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.AERepository;
import com.example.demo.repositories.AssociationRepository;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.CompteRenduRepository;
import com.example.demo.repositories.ContratRepository;
import com.example.demo.repositories.DocumentsRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.InteractionCommercialeRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.PMRepository;
import com.example.demo.repositories.PPRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * approveDossierForContract() echoue toujours en SERVICE_UNAVAILABLE dans les
 * autres tests (Keycloak desactive en test) : le chemin de succes reel
 * (provisionUser Keycloak OK), le cas ENCAISSEMENT_ET_ECOMMERCE (double ligne
 * de contrat) et le cas compte commerçant desactive ne sont donc jamais
 * exerces. Ce test construit un KeycloakAdminService "active" relie a un
 * MockRestServiceServer, avec les vrais repositories JPA.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class ApproveDossierForContractSuccessTest {

    private static final String SERVER_URL = "http://keycloak.test";
    private static final String REALM = "PFE26";

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private InteractionCommercialeRepository interactionCommercialeRepository;

    @Autowired
    private DocumentsRepository documentsRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private PPRepository ppRepository;

    @Autowired
    private PMRepository pmRepository;

    @Autowired
    private AERepository aeRepository;

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private SwitchMonetiqueClient switchMonetiqueClient;

    @Autowired
    private ContratRepository contratRepository;

    @Autowired
    private CompteRenduRepository compteRenduRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private ActivationMailService activationMailService;

    @Autowired
    private AffiliationStatusMailService affiliationStatusMailService;

    @Autowired
    private ServiceDocumentContratAffiliation serviceDocumentContratAffiliation;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DocumentMimeValidator documentMimeValidator;

    @Autowired
    private PdvGeocodingService pdvGeocodingService;

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

    private StaffAffiliationManagementService buildServiceWithMockKeycloak(KeycloakAdminService keycloakAdminService) {
        return new StaffAffiliationManagementService(
            utilisateurRepository,
            commercialeRepository,
            backOfficeRepository,
            commercantRepository,
            dossierAffiliationRepository,
            interactionCommercialeRepository,
            documentsRepository,
            notificationsRepository,
            ppRepository,
            pmRepository,
            aeRepository,
            associationRepository,
            pdvRepository,
            tpeRepository,
            switchMonetiqueClient,
            contratRepository,
            compteRenduRepository,
            passwordHashService,
            activationMailService,
            affiliationStatusMailService,
            serviceDocumentContratAffiliation,
            jwtService,
            keycloakAdminService,
            documentMimeValidator,
            pdvGeocodingService,
            60,
            "uploads/affiliations"
        );
    }

    private void expectProvisioningSequence(MockRestServiceServer server, String email, String keycloakUserId) {
        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(
            SERVER_URL + "/admin/realms/" + REALM + "/users?email=" + email.replace("@", "%40") + "&exact=true"
        ))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CREATED));

        server.expect(requestTo(
            SERVER_URL + "/admin/realms/" + REALM + "/users?email=" + email.replace("@", "%40") + "&exact=true"
        ))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"" + keycloakUserId + "\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/" + keycloakUserId + "/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/" + keycloakUserId))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/" + keycloakUserId + "/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    private back_office persistBackOffice(String email, boolean peutValiderDossiers) {
        utilisateur backOfficeUser = persistUser(email, RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(peutValiderDossiers);
        return backOfficeRepository.save(backOffice);
    }

    @Test
    void approvesRegularTpeDossierEndToEndWhenKeycloakSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        StaffAffiliationManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        back_office backOffice = persistBackOffice("bo.approve.tpe@test.lanacash.ma", true);
        String merchantEmail = "merchant.approve.tpe@test.lanacash.ma";
        utilisateur merchantUser = persistUser(merchantEmail, RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Approve Tpe");
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

        expectProvisioningSequence(server, merchantEmail, "kc-merchant-tpe");

        var response = service.reviewMerchantDossier(
            "Bearer " + tokenFor(backOffice.getUtilisateur()),
            dossierId,
            new AffiliationReviewRequest("ACCEPTE", null)
        );

        assertThat(response.message()).contains("contrat");
        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.CONTRAT_A_SIGNER);
        assertThat(reloaded.getGeneratedContractPath()).isNotBlank();
        utilisateur reloadedMerchant = utilisateurRepository.findById(merchantUser.getId()).orElseThrow();
        assertThat(reloadedMerchant.getKeycloakId()).isEqualTo("kc-merchant-tpe");
        assertThat(reloadedMerchant.getActive()).isFalse();
        server.verify();
    }

    @Test
    void approvesCombinedEncaissementAndEcommerceDossierRegisteringTwoContractLines() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        StaffAffiliationManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        back_office backOffice = persistBackOffice("bo.approve.combined@test.lanacash.ma", true);
        String merchantEmail = "merchant.approve.combined@test.lanacash.ma";
        utilisateur merchantUser = persistUser(merchantEmail, RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Approve Combined");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setTypeAffiliation(TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        expectProvisioningSequence(server, merchantEmail, "kc-merchant-combined");

        service.reviewMerchantDossier(
            "Bearer " + tokenFor(backOffice.getUtilisateur()),
            dossierId,
            new AffiliationReviewRequest("ACCEPTE", null)
        );

        assertThat(contratRepository.findAll())
            .filteredOn(contrat -> dossierId.equals(contrat.getDossierAffiliation().getIdDossier()))
            .hasSize(2);
        server.verify();
    }

    @Test
    void rejectsApprovalWhenMerchantAccountIsDeactivated() {
        RestClient.Builder builder = RestClient.builder();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        StaffAffiliationManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        back_office backOffice = persistBackOffice("bo.approve.deactivated@test.lanacash.ma", true);
        utilisateur merchantUser = persistUser("merchant.approve.deactivated@test.lanacash.ma", RoleUser.COMMERCANT);
        merchantUser.setDateDesactivation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Approve Deactivated");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        assertThatThrownBy(() -> service.reviewMerchantDossier(
            "Bearer " + tokenFor(backOffice.getUtilisateur()),
            dossierId,
            new AffiliationReviewRequest("ACCEPTE", null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("desactive");
    }
}
