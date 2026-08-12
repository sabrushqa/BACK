package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.dto.CreateBackOfficeRequest;
import com.example.demo.dto.CreateCommercialeRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
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
 * createBackOffice/createCommerciale/sendBackOfficeActivation echouent
 * toujours en SERVICE_UNAVAILABLE dans les autres tests car Keycloak est
 * desactive - le chemin de succes reel n'est donc jamais exerce. Ce test
 * construit un KeycloakAdminService "active" relie a un MockRestServiceServer
 * pour exercer ces chemins de succes, avec les vrais repositories JPA.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorAccountCreationSuccessTest {

    private static final String SERVER_URL = "http://keycloak.test";
    private static final String REALM = "PFE26";

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private SwitchMonetiqueClient switchMonetiqueClient;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ActivationMailService activationMailService;

    @Autowired
    private SupervisorNotificationService supervisorNotificationService;

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

    private SupervisorManagementService buildServiceWithMockKeycloak(KeycloakAdminService keycloakAdminService) {
        return new SupervisorManagementService(
            utilisateurRepository,
            backOfficeRepository,
            commercialeRepository,
            commercantRepository,
            switchMonetiqueClient,
            dossierAffiliationRepository,
            pdvRepository,
            passwordHashService,
            jwtService,
            activationMailService,
            keycloakAdminService,
            supervisorNotificationService,
            geocodingService,
            60,
            "http://localhost:4200",
            "2026-07-16"
        );
    }

    private void expectProvisioningSequence(MockRestServiceServer server, String email, String keycloakUserId, String role) {
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

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/" + role))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"" + role + "\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/" + keycloakUserId + "/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    @Test
    void createsBackOfficeEndToEnd() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        SupervisorManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur superviseur = persistUser("superviseur.createbo@test.lanacash.ma", RoleUser.SUPERVISEUR);
        String newEmail = "nouveau.bo.success@test.lanacash.ma";
        expectProvisioningSequence(server, newEmail, "kc-bo-success", "BACK_OFFICE");

        var response = service.createBackOffice(
            "Bearer " + tokenFor(superviseur),
            new CreateBackOfficeRequest(
                "Saidi", "Hicham", newEmail, "BO-SUCCESS-1", "Conformite", true, false, true
            )
        );

        assertThat(response.message()).contains("créé");
        back_office created = backOfficeRepository.findAll().stream()
            .filter(bo -> newEmail.equals(bo.getUtilisateur().getEmail()))
            .findFirst()
            .orElseThrow();
        assertThat(created.getUtilisateur().getKeycloakId()).isEqualTo("kc-bo-success");
        server.verify();
    }

    @Test
    void createsCommercialeEndToEnd() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        SupervisorManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur superviseur = persistUser("superviseur.createcom@test.lanacash.ma", RoleUser.SUPERVISEUR);
        String newEmail = "nouveau.commercial.success@test.lanacash.ma";
        expectProvisioningSequence(server, newEmail, "kc-com-success", "COMMERCIAL");

        var response = service.createCommerciale(
            "Bearer " + tokenFor(superviseur),
            new CreateCommercialeRequest(
                "Bennani", "Youssef", newEmail, "COM-SUCCESS-1", "Casablanca-Settat", "0600000000"
            )
        );

        assertThat(response.message()).contains("créé");
        server.verify();
    }

    @Test
    void sendsBackOfficeActivationEndToEndWhenKeycloakSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        SupervisorManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur superviseur = persistUser("superviseur.sendactivation@test.lanacash.ma", RoleUser.SUPERVISEUR);

        utilisateur backOfficeUser = persistUser("bo.reactivation@test.lanacash.ma", RoleUser.BACK_OFFICE);
        backOfficeUser.setActive(false);
        backOfficeUser.setDateDesactivation(LocalDate.now());
        utilisateurRepository.save(backOfficeUser);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setNom("Tazi");
        backOffice.setPrenom("Samira");
        backOffice = backOfficeRepository.save(backOffice);

        expectProvisioningSequence(server, "bo.reactivation@test.lanacash.ma", "kc-bo-reactivation", "BACK_OFFICE");

        var response = service.sendBackOfficeActivation(
            "Bearer " + tokenFor(superviseur),
            backOffice.getIdBackOffice()
        );

        // Keycloak (simule) reussit la preparation du compte, mais l'envoi d'e-mail
        // (SMTP reel, desactive en test) echoue toujours: le message reflete ce cas
        // realiste "compte pret, e-mail non envoye" plutot que le succes complet.
        assertThat(response.message()).contains("préparé pour activation");

        utilisateur reloaded = utilisateurRepository.findById(backOfficeUser.getId()).orElseThrow();
        assertThat(reloaded.getKeycloakId()).isEqualTo("kc-bo-reactivation");
        assertThat(reloaded.getDateDesactivation()).isNull();
        server.verify();
    }

    @Test
    void sendsCommercialeActivationEndToEndWhenKeycloakSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        SupervisorManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur superviseur = persistUser("superviseur.sendcomactivation@test.lanacash.ma", RoleUser.SUPERVISEUR);

        utilisateur commercialUser = persistUser("commercial.reactivation@test.lanacash.ma", RoleUser.COMMERCIAL);
        commercialUser.setActive(false);
        commercialUser.setDateDesactivation(LocalDate.now());
        utilisateurRepository.save(commercialUser);
        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setNom("Idrissi");
        commerciale.setPrenom("Kenza");
        commerciale = commercialeRepository.save(commerciale);

        expectProvisioningSequence(server, "commercial.reactivation@test.lanacash.ma", "kc-com-reactivation", "COMMERCIAL");

        var response = service.sendCommercialeActivation(
            "Bearer " + tokenFor(superviseur),
            commerciale.getIdCommercial()
        );

        assertThat(response.message()).contains("préparé pour activation");

        utilisateur reloaded = utilisateurRepository.findById(commercialUser.getId()).orElseThrow();
        assertThat(reloaded.getKeycloakId()).isEqualTo("kc-com-reactivation");
        assertThat(reloaded.getDateDesactivation()).isNull();
        server.verify();
    }

    @Test
    void sendsCommercantActivationEndToEndWhenKeycloakSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService keycloakAdminService = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );
        SupervisorManagementService service = buildServiceWithMockKeycloak(keycloakAdminService);

        utilisateur superviseur = persistUser("superviseur.sendmerchantactivation@test.lanacash.ma", RoleUser.SUPERVISEUR);

        utilisateur merchantUser = persistUser("merchant.reactivation@test.lanacash.ma", RoleUser.COMMERCANT);
        merchantUser.setActive(false);
        merchantUser.setDateDesactivation(LocalDate.now());
        utilisateurRepository.save(merchantUser);
        com.example.demo.entities.commercant commercant = new com.example.demo.entities.commercant();
        commercant.setNomCommercial("Boutique Reactivation Test");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        expectProvisioningSequence(server, "merchant.reactivation@test.lanacash.ma", "kc-merchant-reactivation", "COMMERCANT");

        var response = service.sendCommercantActivation(
            "Bearer " + tokenFor(superviseur),
            commercant.getIdCommercant()
        );

        assertThat(response.message()).contains("préparé pour activation");

        utilisateur reloaded = utilisateurRepository.findById(merchantUser.getId()).orElseThrow();
        assertThat(reloaded.getKeycloakId()).isEqualTo("kc-merchant-reactivation");
        assertThat(reloaded.getDateDesactivation()).isNull();
        server.verify();
    }
}
