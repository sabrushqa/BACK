package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Verifie le bootstrap du compte superviseur initial: cree un compte inactif
 * en attente d'activation s'il n'existe pas, ne fait rien si desactive ou si
 * le superviseur existe deja et est actif. Utilise les vrais beans (Keycloak
 * desactive en test => degrade proprement, pas d'appel reseau reel).
 */
@SpringBootTest
@Transactional
class SupervisorBootstrapServiceTest {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PasswordHashService passwordHashService;

    @Autowired
    private ActivationMailService activationMailService;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    private SupervisorBootstrapService buildBootstrap(boolean enabled, String supervisorEmail) {
        return new SupervisorBootstrapService(
            utilisateurRepository,
            passwordHashService,
            activationMailService,
            keycloakAdminService,
            enabled,
            supervisorEmail,
            60
        );
    }

    @Test
    void doesNothingWhenBootstrapDisabled() {
        String email = "supervisor.disabled@test.lanacash.ma";
        buildBootstrap(false, email).run(new DefaultApplicationArguments());

        assertThat(utilisateurRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void createsInactiveSupervisorAccountWhenNoneExists() {
        String email = "supervisor.nouveau@test.lanacash.ma";
        buildBootstrap(true, email).run(new DefaultApplicationArguments());

        Optional<utilisateur> created = utilisateurRepository.findByEmailIgnoreCase(email);
        assertThat(created).isPresent();
        assertThat(created.get().getRole()).isEqualTo(RoleUser.SUPERVISEUR);
        assertThat(created.get().getActive()).isFalse();
    }

    @Test
    void doesNotModifyAlreadyActiveSupervisor() {
        String email = "supervisor.actif@test.lanacash.ma";
        utilisateur existing = new utilisateur();
        existing.setEmail(email);
        existing.setRole(RoleUser.SUPERVISEUR);
        existing.setActive(true);
        existing.setDateCreation(LocalDate.now());
        existing.setTokenVersion(5);
        utilisateurRepository.save(existing);

        buildBootstrap(true, email).run(new DefaultApplicationArguments());

        utilisateur reloaded = utilisateurRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(reloaded.getTokenVersion()).isEqualTo(5);
    }

    @Test
    void skipsWhenExistingAccountHasDifferentRole() {
        String email = "supervisor.wrongrole@test.lanacash.ma";
        utilisateur existing = new utilisateur();
        existing.setEmail(email);
        existing.setRole(RoleUser.COMMERCANT);
        existing.setActive(true);
        existing.setDateCreation(LocalDate.now());
        existing.setTokenVersion(2);
        utilisateurRepository.save(existing);

        buildBootstrap(true, email).run(new DefaultApplicationArguments());

        utilisateur reloaded = utilisateurRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(RoleUser.COMMERCANT);
        assertThat(reloaded.getTokenVersion()).isEqualTo(2);
    }

    @Test
    void reissuesTemporaryPasswordForInactiveExistingSupervisorWhenKeycloakDisabled() {
        String email = "supervisor.inactif@test.lanacash.ma";
        utilisateur existing = new utilisateur();
        existing.setEmail(email);
        existing.setRole(RoleUser.SUPERVISEUR);
        existing.setActive(false);
        existing.setDateCreation(LocalDate.now());
        existing.setTokenVersion(3);
        utilisateurRepository.save(existing);

        buildBootstrap(true, email).run(new DefaultApplicationArguments());

        utilisateur reloaded = utilisateurRepository.findByEmailIgnoreCase(email).orElseThrow();
        // Keycloak desactive en test => provisionUser() renvoie false, le nouveau mot de
        // passe temporaire est prepare mais l'e-mail d'activation n'est jamais envoye.
        assertThat(reloaded.getTokenVersion()).isEqualTo(4);
        assertThat(reloaded.getPasswordExpiresAt()).isNotNull();
    }

    @Test
    void provisionsNewSupervisorEndToEndWhenKeycloakSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String serverUrl = "http://keycloak.test";
        String realm = "PFE26";
        KeycloakAdminService mockKeycloakAdminService = new KeycloakAdminService(
            builder, true, serverUrl, realm, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );

        String email = "supervisor.success@test.lanacash.ma";

        server.expect(requestTo(serverUrl + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(
            serverUrl + "/admin/realms/" + realm + "/users?email=" + email.replace("@", "%40") + "&exact=true"
        ))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(serverUrl + "/admin/realms/" + realm + "/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CREATED));

        server.expect(requestTo(
            serverUrl + "/admin/realms/" + realm + "/users?email=" + email.replace("@", "%40") + "&exact=true"
        ))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-supervisor-success\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(serverUrl + "/admin/realms/" + realm + "/users/kc-supervisor-success/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(serverUrl + "/admin/realms/" + realm + "/users/kc-supervisor-success"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(serverUrl + "/admin/realms/" + realm + "/roles/SUPERVISEUR"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"SUPERVISEUR\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(serverUrl + "/admin/realms/" + realm + "/users/kc-supervisor-success/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        SupervisorBootstrapService bootstrap = new SupervisorBootstrapService(
            utilisateurRepository, passwordHashService, activationMailService,
            mockKeycloakAdminService, true, email, 60
        );

        bootstrap.run(new DefaultApplicationArguments());

        utilisateur reloaded = utilisateurRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(reloaded.getKeycloakId()).isEqualTo("kc-supervisor-success");
        server.verify();
    }
}
