package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import com.example.demo.services.ServiceDocumentContratAffiliation;
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
 * Exerce, via l'API HTTP reelle, les endpoints staff restants: brouillon
 * commercial, interactions, validation de dossier, et telechargement du
 * contrat genere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class StaffAffiliationControllerEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private ServiceDocumentContratAffiliation serviceDocumentContratAffiliation;

    @Autowired
    private com.example.demo.repositories.CommercialeRepository commercialeRepository;

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

    @Test
    void createCommercialDraftViaJsonReturnsOk() {
        utilisateur commercialUser = persistUser("commercial.endpoint.draft@test.lanacash.ma", RoleUser.COMMERCIAL);
        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        String body = """
            {"typeCommercant":"PERSONNE_PHYSIQUE","typeAffiliation":"TPE","email":"nouveau.endpoint.draft@test.lanacash.ma","nom":"Test","prenom":"Draft","cin":"XY998877"}
            """;

        mvc.perform(
            MockMvcRequestBuilders.post("/api/staff/affiliations/drafts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercialUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void reviewMerchantDossierViaJsonReachesReviewLogicWithoutPermissionFlag() {
        // La restriction par permission individuelle (peutValiderDossiers) a ete supprimee :
        // tout agent BACK_OFFICE atteint desormais la logique metier de revue. Ici l'appel
        // echoue en 400 uniquement parce que le dossier de test n'a pas de compte commercant
        // rattache (donnee manquante), pas a cause d'un refus de permission.
        utilisateur backOfficeUser = persistUser("bo.endpoint.review@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(false);
        backOfficeRepository.save(backOffice);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Review Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/staff/affiliations/" + dossier.getIdDossier() + "/review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ACCEPTE\"}")
        ).assertThat().hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void downloadGeneratedContractReturnsOkForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.endpoint.download@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Endpoint Download");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);
        dossier.setGeneratedContractPath(generated.cheminStocke());
        dossier.setGeneratedContractFileName(generated.nomFichier());
        dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/staff/affiliations/" + dossier.getIdDossier() + "/contract/download")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void getRequestsReturnsOverviewForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.endpoint.overview@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Overview Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/staff/affiliations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.requests")
            .asArray()
            .isNotEmpty();
    }

    @Test
    void getRequestsRejectsUnauthenticatedCaller() {
        mvc.perform(MockMvcRequestBuilders.get("/api/staff/affiliations"))
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getAndAddCommercialInteractionsRoundTrip() {
        utilisateur commercialUser = persistUser("commercial.endpoint.interaction@test.lanacash.ma", RoleUser.COMMERCIAL);
        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Interaction Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setCommerciale(commerciale);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/staff/affiliations/" + dossier.getIdDossier() + "/interactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercialUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"typeInteraction\":\"APPEL\",\"resultat\":\"Interesse\",\"commentaire\":\"RDV pris\"}"
                )
        ).assertThat().hasStatus(HttpStatus.OK);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/staff/affiliations/" + dossier.getIdDossier() + "/interactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercialUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.interactions")
            .asArray()
            .isNotEmpty();
    }

    @Test
    void downloadCommercialReportReturnsOkForSupervisor() {
        utilisateur superviseur =
            persistUser("superviseur.endpoint.report@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Report Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        ServiceDocumentContratAffiliation.CompteRenduCommercialGenere generated =
            serviceDocumentContratAffiliation.genererCompteRenduCommercial(dossier);
        dossier.setCommercialReportPath(generated.cheminStocke());
        dossier.setCommercialReportFileName(generated.nomFichier());
        dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.get(
                "/api/staff/affiliations/" + dossier.getIdDossier() + "/commercial-report/download"
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void downloadFullDossierReturnsOkForSupervisor() {
        utilisateur superviseur =
            persistUser("superviseur.endpoint.fulldossier@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique FullDossier Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.get(
                "/api/staff/affiliations/" + dossier.getIdDossier() + "/full-dossier/download"
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void downloadDocumentEndpointReturnsOkForSupervisor() {
        utilisateur superviseur =
            persistUser("superviseur.endpoint.document@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Document Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        // Reutilise un fichier reellement present sur disque (contrat genere)
        // comme stand-in pour un document depose (seul le chemin physique compte).
        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);

        com.example.demo.entities.documents document = new com.example.demo.entities.documents();
        document.setDossierAffiliation(dossier);
        document.setTypeDocument(com.example.demo.enums.TypeDocument.RIB);
        document.setCheminStockage(generated.cheminStocke());
        document.setTailleFichier(1L);
        document.setDateUpload(LocalDate.now());
        document.setStatutDocument(com.example.demo.enums.StatusDocument.UPLOADE);
        document = documentsRepository.save(document);

        mvc.perform(
            MockMvcRequestBuilders.get(
                "/api/staff/affiliations/" + dossier.getIdDossier()
                    + "/documents/" + document.getIdDocument() + "/download"
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void downloadSignedContractEndpointReturnsOkForSupervisor() {
        utilisateur superviseur =
            persistUser("superviseur.endpoint.signed@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Signed Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);
        dossier.setSignedContractPath(generated.cheminStocke());
        dossier.setSignedContractFileName(generated.nomFichier());
        dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.get(
                "/api/staff/affiliations/" + dossier.getIdDossier() + "/contract/signed/download"
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void createCommercialDraftMultipartReturnsOk() {
        utilisateur commercialUser =
            persistUser("commercial.endpoint.draftmultipart@test.lanacash.ma", RoleUser.COMMERCIAL);
        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        String payload = """
            {"typeCommercant":"PERSONNE_PHYSIQUE","typeAffiliation":"TPE","email":"nouveau.endpoint.draftmultipart@test.lanacash.ma","nom":"Test","prenom":"Multipart","cin":"XY112233"}
            """;

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/staff/affiliations/drafts")
                .param("payload", payload)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercialUser))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void saveCommercialDraftViaJsonReturnsOk() {
        utilisateur commercialUser =
            persistUser("commercial.endpoint.savedraft@test.lanacash.ma", RoleUser.COMMERCIAL);
        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Save Draft Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setCommerciale(commerciale);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        String body = """
            {"typeCommercant":"PERSONNE_PHYSIQUE","typeAffiliation":"TPE","nom":"Test","prenom":"SaveDraft","cin":"XY554433"}
            """;

        mvc.perform(
            MockMvcRequestBuilders.post("/api/staff/affiliations/" + dossier.getIdDossier() + "/draft")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercialUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void saveCommercialDraftMultipartReturnsOk() {
        utilisateur commercialUser =
            persistUser("commercial.endpoint.savedraftmultipart@test.lanacash.ma", RoleUser.COMMERCIAL);
        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Save Draft Multipart Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setCommerciale(commerciale);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        String payload = """
            {"typeCommercant":"PERSONNE_PHYSIQUE","typeAffiliation":"TPE","nom":"Test","prenom":"SaveDraftMultipart","cin":"XY665544"}
            """;

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/staff/affiliations/" + dossier.getIdDossier() + "/draft")
                .param("payload", payload)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercialUser))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void completeMerchantDossierViaJsonRejectsWrongRole() {
        utilisateur superviseur =
            persistUser("superviseur.endpoint.complete@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Complete Endpoint");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/staff/affiliations/" + dossier.getIdDossier() + "/complète")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).assertThat().hasStatus(HttpStatus.FORBIDDEN);
    }

    @Autowired
    private com.example.demo.repositories.DocumentsRepository documentsRepository;
}
