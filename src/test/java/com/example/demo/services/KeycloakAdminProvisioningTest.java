package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Exerce le flux complet de provisioning Keycloak (jeton admin, recherche,
 * creation, reinitialisation de mot de passe, activation, attribution de
 * role) via MockRestServiceServer - aucun appel reseau reel, mais toute la
 * logique HTTP de KeycloakAdminService est reellement executee et verifiee.
 */
class KeycloakAdminProvisioningTest {

    private static final String SERVER_URL = "http://keycloak.test";
    private static final String REALM = "PFE26";

    private MockRestServiceServer buildServiceAndServer(KeycloakAdminService[] serviceHolder) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        serviceHolder[0] = new KeycloakAdminService(
            builder,
            true,
            SERVER_URL,
            REALM,
            "portail-affiliation",
            "master",
            "admin-cli",
            "admin",
            "admin-password",
            false
        );
        return server;
    }

    @Test
    void provisionUserCreatesNewKeycloakAccountEndToEnd() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=nouveau.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CREATED));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=nouveau.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-123\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-123/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-123"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-123/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        utilisateur user = new utilisateur();
        user.setEmail("nouveau.kc@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        boolean result = service.provisionUser(user, "TempPass123!");

        assertThat(result).isTrue();
        assertThat(user.getKeycloakId()).isEqualTo("kc-user-123");
        server.verify();
    }

    @Test
    void provisionUserReturnsFalseWhenTokenRequestFails() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        utilisateur user = new utilisateur();
        user.setEmail("echec.kc@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        boolean result = service.provisionUser(user, "TempPass123!");

        assertThat(result).isFalse();
    }

    @Test
    void provisionUserReusesExistingKeycloakAccountByEmail() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=existant.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-existant\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-existant/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-existant"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/BACK_OFFICE"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-2\",\"name\":\"BACK_OFFICE\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-existant/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        utilisateur user = new utilisateur();
        user.setEmail("existant.kc@test.lanacash.ma");
        user.setRole(RoleUser.BACK_OFFICE);

        boolean result = service.provisionUser(user, "TempPass123!");

        assertThat(result).isTrue();
        assertThat(user.getKeycloakId()).isEqualTo("kc-user-existant");
        server.verify();
    }

    @Test
    void setPermanentPasswordActivatesAccountEndToEnd() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=activation.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-activation\"}]", MediaType.APPLICATION_JSON));

        // clearActivationRequirements (1ere fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[\"UPDATE_PASSWORD\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // clearActivationRequirements (2eme fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // otpRequired=false donc applyPostPasswordRequiredActions() ne fait aucun appel.

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // assertActivatedUser
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "{\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[]}",
                MediaType.APPLICATION_JSON
            ));

        utilisateur user = new utilisateur();
        user.setEmail("activation.kc@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        service.setPermanentPassword(user, "NouveauPass123!");

        assertThat(user.getKeycloakId()).isEqualTo("kc-user-activation");
        server.verify();
    }

    @Test
    void setPermanentPasswordAppliesConfigureTotpWhenOtpRequired() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService service = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", true
        );

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=activation.otp%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-otp-activation\"}]", MediaType.APPLICATION_JSON));

        // clearActivationRequirements (1ere fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[\"UPDATE_PASSWORD\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // clearActivationRequirements (2eme fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // applyPostPasswordRequiredActions (otpRequired=true)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // assertActivatedUser: requiredActions doit contenir uniquement CONFIGURE_TOTP quand otpRequired=true.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "{\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[\"CONFIGURE_TOTP\"]}",
                MediaType.APPLICATION_JSON
            ));

        utilisateur user = new utilisateur();
        user.setEmail("activation.otp@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        service.setPermanentPassword(user, "NouveauPass123!");

        assertThat(user.getKeycloakId()).isEqualTo("kc-user-otp-activation");
        server.verify();
    }

    @Test
    void disableUserSendsDisableRequestWhenAccountFound() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=adesactiver.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-disable\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-disable"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        utilisateur user = new utilisateur();
        user.setEmail("adesactiver.kc@test.lanacash.ma");

        service.disableUser(user);

        server.verify();
    }

    @Test
    void passwordMatchesReturnsTrueWhenTokenRequestSucceeds() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/" + REALM + "/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-user-token\"}", MediaType.APPLICATION_JSON));

        utilisateur user = new utilisateur();
        user.setEmail("motdepasse.kc@test.lanacash.ma");

        assertThat(service.passwordMatches(user, "BonMotDePasse1!")).isTrue();
        server.verify();
    }

    @Test
    void passwordMatchesReturnsTrueWhenAccountNotFullySetUp() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/" + REALM + "/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error_description\":\"Account is not fully set up\"}"));

        utilisateur user = new utilisateur();
        user.setEmail("motdepasse.pasactif@test.lanacash.ma");

        assertThat(service.passwordMatches(user, "BonMotDePasse1!")).isTrue();
        server.verify();
    }

    @Test
    void passwordMatchesReturnsFalseForWrongPassword() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/" + REALM + "/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error_description\":\"Invalid user credentials\"}"));

        utilisateur user = new utilisateur();
        user.setEmail("motdepasse.mauvais@test.lanacash.ma");

        assertThat(service.passwordMatches(user, "MauvaisMotDePasse")).isFalse();
        server.verify();
    }

    @Test
    void passwordMatchesReturnsFalseWhenTokenEndpointUnreachable() {
        KeycloakAdminService service = new KeycloakAdminService(
            RestClient.builder(), true, "http://127.0.0.1:1", REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );

        utilisateur user = new utilisateur();
        user.setEmail("motdepasse.injoignable@test.lanacash.ma");

        assertThat(service.passwordMatches(user, "BonMotDePasse1!")).isFalse();
    }

    @Test
    void disableUserDoesNothingWhenAccountNotFoundByEmail() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=introuvable.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        utilisateur user = new utilisateur();
        user.setEmail("introuvable.kc@test.lanacash.ma");

        service.disableUser(user);

        server.verify();
    }

    @Test
    void disableUserUsesExistingKeycloakIdWithoutEmailLookup() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-known-id"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        utilisateur user = new utilisateur();
        user.setEmail("connu.kc@test.lanacash.ma");
        user.setKeycloakId("kc-user-known-id");

        service.disableUser(user);

        server.verify();
    }

    @Test
    void sendPasswordSetupEmailSucceedsWhenUserFound() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=reset.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-reset\"}]", MediaType.APPLICATION_JSON));

        server.expect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        utilisateur user = new utilisateur();
        user.setEmail("reset.kc@test.lanacash.ma");

        assertThat(service.sendPasswordSetupEmail(user, "http://localhost:4200/login")).isTrue();
        server.verify();
    }

    @Test
    void configureClientUpdatesRedirectUrisWhenClientFound() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/clients?clientId=portail-affiliation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"client-uuid-1\",\"clientId\":\"portail-affiliation\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/clients/client-uuid-1"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        boolean result = service.configureClient(
            java.util.List.of("http://localhost:4200/*"),
            java.util.List.of("http://localhost:4200")
        );

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    void configureClientReturnsFalseWhenNoRedirectUriProvided() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        assertThat(service.configureClient(java.util.List.of(), java.util.List.of())).isFalse();
    }

    @Test
    void configureClientReturnsFalseWhenClientNotFoundInRealm() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/clients?clientId=portail-affiliation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        boolean result = service.configureClient(
            java.util.List.of("http://localhost:4200/*"),
            java.util.List.of()
        );

        assertThat(result).isFalse();
        server.verify();
    }

    @Test
    void configureClientUsesWildcardWebOriginWhenNoneProvided() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/clients?clientId=portail-affiliation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "[{\"id\":\"client-uuid-2\",\"clientId\":\"portail-affiliation\",\"attributes\":{}}]",
                MediaType.APPLICATION_JSON
            ));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/clients/client-uuid-2"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        boolean result = service.configureClient(
            java.util.List.of("http://localhost:4200/*"),
            java.util.List.of()
        );

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    void configureClientReturnsFalseWhenClientRepresentationHasNoUuid() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        // Representation client trouvee mais sans champ "id" exploitable:
        // impossible de construire l'URL de mise a jour.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/clients?clientId=portail-affiliation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"clientId\":\"portail-affiliation\"}]", MediaType.APPLICATION_JSON));

        boolean result = service.configureClient(
            java.util.List.of("http://localhost:4200/*"),
            java.util.List.of()
        );

        assertThat(result).isFalse();
        server.verify();
    }

    @Test
    void requireOtpForExistingUsersIsNoOpWhenOtpNotRequired() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService service = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", false
        );

        assertThat(service.requireOtpForExistingUsers()).isTrue();
        server.verify();
    }

    @Test
    void requireOtpForExistingUsersAppliesConfigureTotpToUsersWithoutIt() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService service = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", true
        );

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?max=500"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "[{\"id\":\"kc-user-otp\",\"enabled\":true,\"requiredActions\":[]}]",
                MediaType.APPLICATION_JSON
            ));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp/credentials"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-otp"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(service.requireOtpForExistingUsers()).isTrue();
        server.verify();
    }

    @Test
    void setPermanentPasswordCreatesActivatedAccountWhenNoneExistsAndCreatesMissingRole() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        // resolveExistingUserId: pas de keycloakId connu, recherche par e-mail sans resultat.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=nouveau.activation.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // createActivatedUser: creation puis relecture par e-mail.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=nouveau.activation.kc%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-new-activation\"}]", MediaType.APPLICATION_JSON));

        // clearActivationRequirements (1ere fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-new-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[\"UPDATE_PASSWORD\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-new-activation"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-new-activation/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // clearActivationRequirements (2eme fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-new-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-new-activation"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // assignRealmRole: le role n'existe pas encore -> creation puis relecture.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-new\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-new-activation/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // assertActivatedUser
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-new-activation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "{\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[]}",
                MediaType.APPLICATION_JSON
            ));

        utilisateur user = new utilisateur();
        user.setEmail("nouveau.activation.kc@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        service.setPermanentPassword(user, "NouveauPass123!");

        assertThat(user.getKeycloakId()).isEqualTo("kc-user-new-activation");
        server.verify();
    }

    @Test
    void configureRealmSmtpSucceedsWithAuthenticatedCredentials() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        boolean result = service.configureRealmSmtp(
            "smtp.gmail.com",
            "587",
            "no-reply@lanacash.ma",
            "Lana Cash",
            "no-reply@lanacash.ma",
            "app-password",
            true,
            false,
            "support@lanacash.ma"
        );

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    void configureRealmSmtpReturnsFalseWhenHostIsBlank() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        boolean result = service.configureRealmSmtp(
            "", "587", "no-reply@lanacash.ma", "Lana Cash",
            "", "", true, false, ""
        );

        assertThat(result).isFalse();
    }

    @Test
    void configureRealmSmtpReturnsFalseWhenAdminRequestFails() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        boolean result = service.configureRealmSmtp(
            "smtp.gmail.com", "587", "no-reply@lanacash.ma", "Lana Cash",
            "", "", true, false, ""
        );

        assertThat(result).isFalse();
        server.verify();
    }

    @Test
    void sendPasswordSetupEmailUsesExistingKeycloakIdWithoutEmailLookup() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        // Aucune requete GET /users?email=... attendue: le keycloakId deja
        // connu doit etre reutilise directement.
        server.expect(requestTo(SERVER_URL
            + "/admin/realms/" + REALM + "/users/kc-known-id/execute-actions-email?client_id=portail-affiliation"
            + "&redirect_uri=http%3A%2F%2Flocalhost%3A4200%2Flogin"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        utilisateur user = new utilisateur();
        user.setEmail("reset.kc.knownid@test.lanacash.ma");
        user.setKeycloakId("kc-known-id");

        assertThat(service.sendPasswordSetupEmail(user, "http://localhost:4200/login")).isTrue();
        server.verify();
    }

    @Test
    void sendPasswordSetupEmailReturnsFalseWhenUserNotFoundByEmail() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=reset.notfound%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        utilisateur user = new utilisateur();
        user.setEmail("reset.notfound@test.lanacash.ma");

        assertThat(service.sendPasswordSetupEmail(user, "http://localhost:4200/login")).isFalse();
        server.verify();
    }

    @Test
    void setPermanentPasswordReusesExistingKeycloakIdWithoutEmailLookup() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        // resolveExistingUserId: GET direct sur le keycloakId deja connu,
        // aucune recherche par e-mail.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-existing-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"kc-existing-id\",\"enabled\":false}", MediaType.APPLICATION_JSON));

        // clearActivationRequirements (1ere fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-existing-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[\"UPDATE_PASSWORD\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-existing-id"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-existing-id/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // clearActivationRequirements (2eme fois)
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-existing-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-existing-id"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-existing-id/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-existing-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "{\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[]}",
                MediaType.APPLICATION_JSON
            ));

        utilisateur user = new utilisateur();
        user.setEmail("activation.existingid@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setKeycloakId("kc-existing-id");

        service.setPermanentPassword(user, "NouveauPass123!");

        assertThat(user.getKeycloakId()).isEqualTo("kc-existing-id");
        server.verify();
    }

    @Test
    void setPermanentPasswordFallsBackToEmailLookupWhenKnownKeycloakIdIs404() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        // Le keycloakId stocke localement n'existe plus cote Keycloak (404):
        // resolveExistingUserId doit retomber sur la recherche par e-mail.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-stale-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=activation.staleid%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-fresh-id\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-fresh-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[\"UPDATE_PASSWORD\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-fresh-id"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-fresh-id/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-fresh-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-fresh-id"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-fresh-id/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-fresh-id"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "{\"enabled\":true,\"emailVerified\":true,\"requiredActions\":[]}",
                MediaType.APPLICATION_JSON
            ));

        utilisateur user = new utilisateur();
        user.setEmail("activation.staleid@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);
        user.setKeycloakId("kc-stale-id");

        service.setPermanentPassword(user, "NouveauPass123!");

        assertThat(user.getKeycloakId()).isEqualTo("kc-fresh-id");
        server.verify();
    }

    @Test
    void setPermanentPasswordThrowsWhenActivationVerificationFails() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=activation.incomplete%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-incomplete\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-incomplete"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[\"UPDATE_PASSWORD\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-incomplete"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-incomplete/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-incomplete"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"enabled\":false,\"requiredActions\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-incomplete"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-incomplete/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        // assertActivatedUser: Keycloak indique que le compte n'est toujours
        // pas active malgre toute la sequence precedente.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-incomplete"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "{\"enabled\":false,\"emailVerified\":true,\"requiredActions\":[]}",
                MediaType.APPLICATION_JSON
            ));

        utilisateur user = new utilisateur();
        user.setEmail("activation.incomplete@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        assertThat(
            org.assertj.core.api.Assertions.catchThrowable(() -> service.setPermanentPassword(user, "NouveauPass123!"))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Activation Keycloak incomplète");
        server.verify();
    }

    @Test
    void sendPasswordSetupEmailReturnsFalseWhenAdminRequestFails() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        utilisateur user = new utilisateur();
        user.setEmail("reset.kc.tokenfail@test.lanacash.ma");

        assertThat(service.sendPasswordSetupEmail(user, "http://localhost:4200/login")).isFalse();
        server.verify();
    }

    @Test
    void sendPasswordSetupEmailUsesOtpRequiredActionsWhenOtpRequired() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService service = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", true
        );

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=reset.kc.otp%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-reset-otp\"}]", MediaType.APPLICATION_JSON));

        server.expect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        utilisateur user = new utilisateur();
        user.setEmail("reset.kc.otp@test.lanacash.ma");

        assertThat(service.sendPasswordSetupEmail(user, "http://localhost:4200/login")).isTrue();
        server.verify();
    }

    @Test
    void requireOtpForExistingUsersReturnsFalseWhenAdminRequestFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService service = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", true
        );

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(service.requireOtpForExistingUsers()).isFalse();
        server.verify();
    }

    @Test
    void requireOtpForExistingUsersSkipsUsersWhoAlreadyHaveOtpConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService service = new KeycloakAdminService(
            builder, true, SERVER_URL, REALM, "portail-affiliation",
            "master", "admin-cli", "admin", "admin-password", true
        );

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?max=500"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                "[{\"id\":\"kc-user-has-otp\",\"enabled\":true,\"requiredActions\":[]}]",
                MediaType.APPLICATION_JSON
            ));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-has-otp/credentials"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"type\":\"otp\"}]", MediaType.APPLICATION_JSON));

        // Aucune requete PUT attendue: l'utilisateur a deja OTP configure, il est ignore.
        boolean result = service.requireOtpForExistingUsers();

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    void disableUserDoesNothingWhenAdminRequestFails() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        utilisateur user = new utilisateur();
        user.setEmail("disable.tokenfail@test.lanacash.ma");
        user.setKeycloakId("kc-disable-tokenfail");

        assertThatCode(() -> service.disableUser(user)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void configureClientReturnsFalseWhenUpdateRequestFails() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/clients?clientId=portail-affiliation"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"client-uuid-fail\",\"clientId\":\"portail-affiliation\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/clients/client-uuid-fail"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        boolean result = service.configureClient(
            java.util.List.of("http://localhost:4200/*"),
            java.util.List.of()
        );

        assertThat(result).isFalse();
        server.verify();
    }

    @Test
    void adminAccessTokenFailureIsPropagatedBySetPermanentPassword() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        // Reponse 200 mais sans champ access_token exploitable: adminAccessToken()
        // doit lever IllegalStateException, non capturee par setPermanentPassword.
        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));

        utilisateur user = new utilisateur();
        user.setEmail("activation.notoken@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        assertThat(
            org.assertj.core.api.Assertions.catchThrowable(() -> service.setPermanentPassword(user, "NouveauPass123!"))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Token admin Keycloak introuvable");
        server.verify();
    }

    @Test
    void assignRealmRoleIgnoresConflictWhenRoleAlreadyAssigned() {
        KeycloakAdminService[] holder = new KeycloakAdminService[1];
        MockRestServiceServer server = buildServiceAndServer(holder);
        KeycloakAdminService service = holder[0];

        server.expect(requestTo(SERVER_URL + "/realms/master/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\":\"fake-admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users?email=provision.roleconflict%40test.lanacash.ma&exact=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("[{\"id\":\"kc-user-roleconflict\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-roleconflict/reset-password"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-roleconflict"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/COMMERCANT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"COMMERCANT\"}", MediaType.APPLICATION_JSON));

        // Le role est deja affecte a cet utilisateur: Keycloak repond 409, ignore silencieusement.
        server.expect(requestTo(SERVER_URL + "/admin/realms/" + REALM + "/users/kc-user-roleconflict/role-mappings/realm"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.CONFLICT));

        utilisateur user = new utilisateur();
        user.setEmail("provision.roleconflict@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        boolean result = service.provisionUser(user, "TempPass123!");

        assertThat(result).isTrue();
        server.verify();
    }
}
