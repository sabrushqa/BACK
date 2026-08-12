package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie la validation et la detection de doublon lors de la creation d'un
 * compte back-office par le superviseur, avant tout appel a Keycloak (qui
 * est desactive en test - APP_KEYCLOAK_ADMIN_ENABLED=false).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class SupervisorBackOfficeCreationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private MockMvcTester mvc;
    private utilisateur superviseurUser;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);
        superviseurUser = persistUser("superviseur.creation.test@lanacash.ma", RoleUser.SUPERVISEUR);
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
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    private String requestBody(String email) {
        return """
            {
              "nom": "Doe",
              "prenom": "Jane",
              "email": "%s",
              "matricule": "BO-001",
              "service": "Support",
              "peutValiderDossiers": true,
              "peutAffecterTpe": false,
              "peutGererReclamations": false
            }
            """.formatted(email);
    }

    @Test
    void rejectsMissingRequiredField() {
        String bodyWithoutNom = """
            {
              "nom": "",
              "prenom": "Jane",
              "email": "bo.missingfield@test.lanacash.ma",
              "matricule": "BO-001",
              "service": "Support"
            }
            """;

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/back-offices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseurUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithoutNom)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsDuplicateEmail() {
        persistUser("bo.duplicate@test.lanacash.ma", RoleUser.BACK_OFFICE);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/back-offices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseurUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody("bo.duplicate@test.lanacash.ma"))
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returnsServiceUnavailableWhenKeycloakProvisioningIsDisabled() {
        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/back-offices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseurUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody("bo.nouveau@test.lanacash.ma"))
        )
            .assertThat()
            .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsNonSupervisorRole() {
        utilisateur commercantUser = persistUser("commercant.creation.test@lanacash.ma", RoleUser.COMMERCANT);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/back-offices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody("bo.viacommercant@test.lanacash.ma"))
        )
            .assertThat()
            .hasStatus(HttpStatus.FORBIDDEN);
    }
}
