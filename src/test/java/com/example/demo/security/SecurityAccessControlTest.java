package com.example.demo.security;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preuve que les endpoints reserves (superviseur, staff) refusent bien un
 * utilisateur authentifie mais du mauvais role, et qu'ils exigent un token
 * valide. Tourne avec le contexte Spring complet contre master5_test (SQL
 * Server reel), pas H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class SecurityAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private MockMvcTester mvc;

    private utilisateur commercantUser;
    private utilisateur superviseurUser;
    private utilisateur backOfficeUser;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);
        commercantUser = persistUser("commercant.securite@test.lanacash.ma", RoleUser.COMMERCANT);
        superviseurUser = persistUser("superviseur.securite@test.lanacash.ma", RoleUser.SUPERVISEUR);
        backOfficeUser = persistUser("backoffice.securite@test.lanacash.ma", RoleUser.BACK_OFFICE);
    }

    private utilisateur persistUser(String email, RoleUser role) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken(
            "kc-sub-" + user.getId(),
            user.getEmail(),
            300
        );
    }

    @Test
    void supervisorEndpointRejectsRequestWithoutToken() {
        mvc.perform(MockMvcRequestBuilders.get("/api/supervisor/overview"))
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void supervisorEndpointRejectsAuthenticatedMerchant() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/supervisor/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void supervisorEndpointRejectsAuthenticatedBackOffice() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/supervisor/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void supervisorEndpointAcceptsSupervisor() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/supervisor/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseurUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK);
    }

    @Test
    void supervisorEndpointRejectsExpiredToken() {
        String expiredToken = TestJwtSupport.mintExpiredToken(
            "kc-sub-" + superviseurUser.getId(),
            superviseurUser.getEmail()
        );
        mvc.perform(
            MockMvcRequestBuilders.get("/api/supervisor/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
        )
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void supervisorEndpointRejectsForgedTokenWithWrongSignature() {
        String forgedToken = TestJwtSupport.mintKeycloakToken(
            "kc-sub-" + superviseurUser.getId(),
            superviseurUser.getEmail(),
            300
        ) + "tampered";
        mvc.perform(
            MockMvcRequestBuilders.get("/api/supervisor/overview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken)
        )
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void staffAffiliationsEndpointRejectsAuthenticatedMerchant() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/staff/affiliations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffAffiliationsEndpointAcceptsBackOffice() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/staff/affiliations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK);
    }

    @Test
    void backOfficeReclamationsEndpointRejectsAuthenticatedMerchant() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
                .accept(MediaType.APPLICATION_JSON)
        )
            .assertThat()
            .hasStatus(HttpStatus.FORBIDDEN);
    }
}
