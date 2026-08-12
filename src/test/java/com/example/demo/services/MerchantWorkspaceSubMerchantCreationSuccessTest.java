package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.dto.MerchantSubMerchantCreateRequest;
import com.example.demo.dto.MerchantSubMerchantCreateResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.SousCommercantRepository;
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
 * createSubMerchant() echoue toujours en SERVICE_UNAVAILABLE dans les autres
 * tests car Keycloak est desactive (isReady()=false) - le chemin de succes
 * reel (creation effective du sous-commerçant, affectation du PDV ou du
 * canal e-commerce) n'est donc jamais exerce par ailleurs. Ce test construit
 * un KeycloakAdminService "active" relie a un MockRestServiceServer pour
 * exercer ce chemin de succes complet, avec les vrais repositories JPA.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantWorkspaceSubMerchantCreationSuccessTest {

    private static final String SERVER_URL = "http://keycloak.test";
    private static final String REALM = "PFE26";

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
    private GeocodingService geocodingService;

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

    private MerchantWorkspaceManagementService buildServiceWithMockKeycloak(
        KeycloakAdminService keycloakAdminService
    ) {
        return new MerchantWorkspaceManagementService(
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
            60
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

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/SOUS_COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"SOUS_COMMERCANT\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/" + keycloakUserId + "/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    @Test
    void createsSubMerchantAssignedToPdvEndToEnd() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        MerchantWorkspaceManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur merchantUser = persistUser("commercant.subcreate.success@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente.setStatut("ACTIF");
        pointVente = pdvRepository.save(pointVente);
        final Long pdvId = pointVente.getIdPDV();

        String subEmail = "sous.commercant.success@test.lanacash.ma";
        expectProvisioningSequence(server, subEmail, "kc-sub-success");

        MerchantSubMerchantCreateResponse response = service.createSubMerchant(
            "Bearer " + tokenFor(merchantUser),
            new MerchantSubMerchantCreateRequest(pdvId, null, "Bennani", "Omar", subEmail, "0600000002")
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.message()).contains("point de vente");
        server.verify();

        pdv reloadedPdv = pdvRepository.findById(pdvId).orElseThrow();
        assertThat(reloadedPdv.getSousCommercant()).isNotNull();
        assertThat(reloadedPdv.getSousCommercant().getEmail()).isEqualTo(subEmail);
    }

    @Test
    void createsSubMerchantAttachedToEcommerceChannelEndToEnd() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        MerchantWorkspaceManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur merchantUser = persistUser("commercant.subcreate.ecommerce@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        String subEmail = "sous.commercant.ecommerce@test.lanacash.ma";
        expectProvisioningSequence(server, subEmail, "kc-sub-ecommerce");

        MerchantSubMerchantCreateResponse response = service.createSubMerchant(
            "Bearer " + tokenFor(merchantUser),
            new MerchantSubMerchantCreateRequest(null, "SITE_MARCHAND", "Tazi", "Yasmine", subEmail, "0600000003")
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.message()).contains("canal");
        server.verify();

        var savedSousCommercant = sousCommercantRepository.findById(response.id()).orElseThrow();
        assertThat(savedSousCommercant.getCanalEcommerce()).isEqualTo("SITE_MARCHAND");
        assertThat(savedSousCommercant.getEmail()).isEqualTo(subEmail);
    }
}
