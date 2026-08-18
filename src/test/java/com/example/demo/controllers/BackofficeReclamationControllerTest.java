package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.Reclamation;
import com.example.demo.entities.back_office;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce, via l'API HTTP reelle, les endpoints back-office de gestion des
 * reclamations: liste filtree, statistiques, indicateurs du tableau de bord,
 * et mise a jour du statut — avec verification du controle de permission
 * peutGererReclamations pour le role BACK_OFFICE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class BackofficeReclamationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private ReclamationRepository reclamationRepository;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);
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

    private Reclamation newReclamation(String statut, String priorite, String type) {
        Reclamation reclamation = new Reclamation();
        reclamation.setDateCreation(LocalDate.now());
        reclamation.setReferenceChat("CHAT-ENDPOINT-1");
        reclamation.setTypeProbleme(type);
        reclamation.setDescription("Probleme signale par le commercant.");
        reclamation.setStatut(statut);
        reclamation.setPriorite(priorite);
        return reclamationRepository.save(reclamation);
    }

    @Test
    void listsReclamationsForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.reclamation.list@test.lanacash.ma", RoleUser.SUPERVISEUR);
        newReclamation("EN_ATTENTE", "HAUTE", "RESEAU");

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void listsReclamationsFilteredByStatut() {
        utilisateur superviseur = persistUser("superviseur.reclamation.filter@test.lanacash.ma", RoleUser.SUPERVISEUR);
        newReclamation("RESOLU", "BASSE", "MATERIEL");

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations").queryParam("statut", "RESOLU")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void backOfficeWithoutPermissionFlagStillHasAccess() {
        // La restriction par permission individuelle (peutGererReclamations) a ete supprimee :
        // tout agent BACK_OFFICE a acces a la gestion des reclamations.
        utilisateur backOfficeUser =
            persistUser("bo.reclamation.sanspermission@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutGererReclamations(false);
        backOfficeRepository.save(backOffice);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void returnsStatsForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.reclamation.stats@test.lanacash.ma", RoleUser.SUPERVISEUR);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.total")
            .isNotNull();
    }

    @Test
    void returnsDashboardForSupervisor() {
        utilisateur superviseur =
            persistUser("superviseur.reclamation.dashboard@test.lanacash.ma", RoleUser.SUPERVISEUR);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations/dashboard").queryParam("days", "7")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void updatesReclamationStatutForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.reclamation.update@test.lanacash.ma", RoleUser.SUPERVISEUR);
        Reclamation reclamation = newReclamation("EN_ATTENTE", "MOYENNE", "AUTRE");

        mvc.perform(
            MockMvcRequestBuilders.patch(
                "/api/backoffice/reclamations/" + reclamation.getIdReclamation() + "/statut"
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\":\"RESOLU\"}")
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void rejectsUpdateWithoutStatutField() {
        utilisateur superviseur =
            persistUser("superviseur.reclamation.updatevide@test.lanacash.ma", RoleUser.SUPERVISEUR);
        Reclamation reclamation = newReclamation("EN_ATTENTE", "MOYENNE", "AUTRE");

        mvc.perform(
            MockMvcRequestBuilders.patch(
                "/api/backoffice/reclamations/" + reclamation.getIdReclamation() + "/statut"
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).assertThat().hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void generatesPdfForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.reclamation.pdf@test.lanacash.ma", RoleUser.SUPERVISEUR);
        Reclamation reclamation = newReclamation("EN_ATTENTE", "HAUTE", "MATERIEL");
        reclamation.setResumeCourt("Panne matérielle");
        reclamation.setCommentaire("Diagnostic technique detaille.");
        reclamationRepository.save(reclamation);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations/" + reclamation.getIdReclamation() + "/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .hasHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE);
    }

    @Test
    void generatesPdfForBackOfficeStaff() {
        utilisateur backOfficeUser = persistUser("bo.reclamation.pdf@test.lanacash.ma", RoleUser.BACK_OFFICE);
        Reclamation reclamation = newReclamation("EN_ATTENTE", "MOYENNE", "CONNECTIVITE");

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations/" + reclamation.getIdReclamation() + "/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void rejectsPdfWithoutToken() {
        Reclamation reclamation = newReclamation("EN_ATTENTE", "MOYENNE", "AUTRE");

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations/" + reclamation.getIdReclamation() + "/pdf")
        ).assertThat().hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsPdfForNonStaffRole() {
        utilisateur commercantUser = persistUser("commercant.reclamation.pdf@test.lanacash.ma", RoleUser.COMMERCANT);
        Reclamation reclamation = newReclamation("EN_ATTENTE", "MOYENNE", "AUTRE");

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations/" + reclamation.getIdReclamation() + "/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        ).assertThat().hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void returnsNotFoundForUnknownReclamationPdf() {
        utilisateur superviseur =
            persistUser("superviseur.reclamation.pdf.notfound@test.lanacash.ma", RoleUser.SUPERVISEUR);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/backoffice/reclamations/999999/pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.NOT_FOUND);
    }
}
