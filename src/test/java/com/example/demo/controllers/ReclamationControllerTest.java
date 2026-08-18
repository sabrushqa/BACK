package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.Reclamation;
import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.ReclamationRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prouve qu'un commercant ne voit jamais les reclamations d'un autre
 * commercant: la liste est resolue depuis le token, sans ID client.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class ReclamationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private ReclamationRepository reclamationRepository;

    private MockMvcTester mvc;
    private utilisateur commercantAUser;
    private utilisateur commercantBUser;
    private Reclamation reclamationDeA;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);

        commercantAUser = persistUser("reclamation.a@test.lanacash.ma");
        commercant commercantA = new commercant();
        commercantA.setUtilisateur(commercantAUser);
        commercantA = commercantRepository.save(commercantA);

        commercantBUser = persistUser("reclamation.b@test.lanacash.ma");
        commercant commercantB = new commercant();
        commercantB.setUtilisateur(commercantBUser);
        commercantB = commercantRepository.save(commercantB);

        reclamationDeA = new Reclamation();
        reclamationDeA.setCommercant(commercantA);
        reclamationDeA.setTypeProbleme("CONNECTIVITE");
        reclamationDeA.setDescription("Probleme de connexion TPE");
        reclamationDeA.setStatut("EN_ATTENTE");
        reclamationDeA.setPriorite("HAUTE");
        reclamationDeA.setDateCreation(LocalDate.now());
        reclamationDeA = reclamationRepository.save(reclamationDeA);
    }

    private utilisateur persistUser(String email) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void merchantSeesOnlyOwnReclamations() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/merchant/reclamations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantAUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.length()")
            .isEqualTo(1);
    }

    @Test
    void otherMerchantSeesEmptyList() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/merchant/reclamations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantBUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.length()")
            .isEqualTo(0);
    }

    @Test
    void rejectsRequestWithoutAuthentication() {
        mvc.perform(MockMvcRequestBuilders.get("/api/merchant/reclamations"))
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createsReclamationForAuthenticatedMerchant() {
        mvc.perform(
            MockMvcRequestBuilders.post("/api/merchant/reclamations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantAUser))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    "{\"referenceChat\":\"CHAT-1\",\"typeProbleme\":\"CONNECTIVITE\","
                        + "\"description\":\"Le TPE ne repond plus.\",\"priorite\":\"HAUTE\"}"
                )
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.typeProbleme")
            .isEqualTo("CONNECTIVITE");
    }

    @Test
    void ownerCanDownloadPdf() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/merchant/reclamations/" + reclamationDeA.getIdReclamation() + "/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantAUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .hasHeader(HttpHeaders.CONTENT_TYPE, org.springframework.http.MediaType.APPLICATION_PDF_VALUE);
    }

    @Test
    void otherMerchantCannotDownloadPdf() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/merchant/reclamations/" + reclamationDeA.getIdReclamation() + "/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantBUser))
        ).assertThat().hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsPdfDownloadWithoutAuthentication() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/merchant/reclamations/" + reclamationDeA.getIdReclamation() + "/pdf")
        ).assertThat().hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsNotFoundForUnknownReclamationPdf() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/merchant/reclamations/999999/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantAUser))
        ).assertThat().hasStatus(HttpStatus.NOT_FOUND);
    }
}
