package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.dto.ActivationAccountRequest;
import com.example.demo.dto.MerchantSessionResponse;
import com.example.demo.dto.PasswordResetChallengeResponse;
import com.example.demo.dto.PasswordResetRequest;
import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.SousCommercantRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.TransactionsRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * requestPasswordReset() depend entierement de KeycloakAdminService pour
 * l'envoi reel de l'e-mail Keycloak, desactive en test (isReady()=false),
 * ce qui empeche d'atteindre le chemin de succes via le bean Spring
 * standard. Ce test construit un KeycloakAdminService distinct, "active",
 * relie a un MockRestServiceServer, pour exercer le chemin de succes reel
 * (aucun appel reseau, mais toute la logique HTTP est executee), avec les
 * vrais repositories JPA pour le reste.
 */
@SpringBootTest
@Transactional
class MerchantAccessServiceKeycloakFlowsTest {

    private static final String SERVER_URL = "http://keycloak.test";
    private static final String REALM = "PFE26";

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private SousCommercantRepository sousCommercantRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private TransactionsRepository transactionsRepository;

    @Autowired
    private SwitchMonetiqueClient switchMonetiqueClient;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private JwtService jwtService;

    private MerchantAccessService buildServiceWithMockKeycloak(KeycloakAdminService keycloakAdminService) {
        return new MerchantAccessService(
            utilisateurRepository,
            commercantRepository,
            backOfficeRepository,
            commercialeRepository,
            dossierAffiliationRepository,
            pdvRepository,
            sousCommercantRepository,
            tpeRepository,
            transactionsRepository,
            switchMonetiqueClient,
            passwordHashService,
            jwtService,
            keycloakAdminService,
            15,
            "http://localhost:4200"
        );
    }

    @Test
    void requestPasswordResetSucceedsWhenKeycloakEmailIsSent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        MerchantAccessService merchantAccessService = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur user = new utilisateur();
        user.setEmail("reset.success@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        utilisateurRepository.save(user);

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(
            SERVER_URL + "/admin/realms/" + REALM + "/users?email=reset.success%40test.lanacash.ma&exact=true"
        ))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-reset-success\"}]", MediaType.APPLICATION_JSON));

        server.expect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        PasswordResetChallengeResponse response = merchantAccessService.requestPasswordReset(
            new PasswordResetRequest("reset.success@test.lanacash.ma")
        );

        assertThat(response.deliveryHint()).contains("*");
        assertThat(response.expiresAt()).isNotNull();
        server.verify();
    }

    @Test
    void requestPasswordResetFailsWhenKeycloakUserNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        MerchantAccessService merchantAccessService = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur user = new utilisateur();
        user.setEmail("reset.notfound@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        utilisateurRepository.save(user);

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(
            SERVER_URL + "/admin/realms/" + REALM + "/users?email=reset.notfound%40test.lanacash.ma&exact=true"
        ))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            merchantAccessService.requestPasswordReset(new PasswordResetRequest("reset.notfound@test.lanacash.ma"))
        )
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .extracting(exception -> ((org.springframework.web.server.ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void activateAccountSucceedsEndToEndWhenKeycloakAccountExists() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        MerchantAccessService merchantAccessService = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur user = new utilisateur();
        user.setEmail("activation.success@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(false);
        user.setPassword(passwordHashService.hash("TempPass123!"));
        user.setPasswordExpiresAt(java.time.LocalDateTime.now().plusMinutes(30));
        user.setDateCreation(LocalDate.now());
        user = utilisateurRepository.save(user);

        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        commercant.setNomCommercial("Boutique Activation Success");
        commercantRepository.save(commercant);

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(
            SERVER_URL + "/admin/realms/" + REALM + "/users?email=activation.success%40test.lanacash.ma&exact=true"
        ))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-activation-success\"}]", MediaType.APPLICATION_JSON));

        // clearActivationRequirements (1ere fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation-success"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[\"UPDATE_PASSWORD\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation-success"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation-success/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // clearActivationRequirements (2eme fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation-success"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation-success"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation-success/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation-success"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "{\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[]}",
                MediaType.APPLICATION_JSON
            ));

        MerchantSessionResponse response = merchantAccessService.activateAccount(
            new ActivationAccountRequest("activation.success@test.lanacash.ma", "TempPass123!", "NouveauPass123!")
        );

        assertThat(response.email()).isEqualTo("activation.success@test.lanacash.ma");
        utilisateur reloaded = utilisateurRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getActive()).isTrue();
        assertThat(reloaded.getKeycloakId()).isEqualTo("kc-user-activation-success");
        server.verify();
    }
}
