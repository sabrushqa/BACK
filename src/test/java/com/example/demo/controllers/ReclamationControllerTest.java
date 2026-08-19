package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.Reclamation;
import com.example.demo.entities.commercant;
import com.example.demo.entities.pdv;
import com.example.demo.entities.sous_commercant;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.ReclamationRepository;
import com.example.demo.repositories.SousCommercantRepository;
import com.example.demo.repositories.TpeRepository;
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

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private SousCommercantRepository sousCommercantRepository;

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

    /**
     * Un sous-commerçant n'a pas de ligne "commercant" a lui — sans la
     * resolution vers le commercant PARENT (ReclamationService::
     * resolveCommercantFor), cette creation echouait avec 403 "Compte
     * commerçant requis." pour tout ticket chatbot cree par un sous-commerçant.
     */
    @Test
    void createsReclamationForSousCommercant() {
        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV du sous-commerçant");
        pointVente.setCommercant(commercantRepository.findByUtilisateur_Id(commercantAUser.getId()).orElseThrow());
        pointVente = pdvRepository.save(pointVente);

        utilisateur subUser = persistUser("reclamation.sub@test.lanacash.ma");
        subUser.setRole(RoleUser.SOUS_COMMERCANT);
        utilisateurRepository.save(subUser);
        sous_commercant sousCommercant = new sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        pointVente.setSousCommercant(sousCommercant);
        pdvRepository.save(pointVente);
        sousCommercantRepository.save(sousCommercant);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/merchant/reclamations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(subUser))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    "{\"referenceChat\":\"CHAT-SOUS-1\",\"typeProbleme\":\"CONNECTIVITE\","
                        + "\"description\":\"Le TPE ne repond plus.\",\"priorite\":\"HAUTE\"}"
                )
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED);
    }

    /**
     * Un sous-commerçant ne doit voir que les reclamations liees a un TPE de
     * SON PDV, pas tout l'historique du commercant parent (meme principe que
     * pour les transactions/TPE de son propre dashboard).
     */
    @Test
    void sousCommercantSeesOnlyReclamationsOfOwnPdv() {
        commercant commercantA = commercantRepository.findByUtilisateur_Id(commercantAUser.getId()).orElseThrow();

        pdv pdvDuSousCommercant = new pdv();
        pdvDuSousCommercant.setNomPDV("PDV du sous-commerçant");
        pdvDuSousCommercant.setCommercant(commercantA);
        pdvDuSousCommercant = pdvRepository.save(pdvDuSousCommercant);

        pdv autrePdv = new pdv();
        autrePdv.setNomPDV("Autre PDV du meme commercant");
        autrePdv.setCommercant(commercantA);
        autrePdv = pdvRepository.save(autrePdv);

        utilisateur subUser = persistUser("reclamation.sub2@test.lanacash.ma");
        subUser.setRole(RoleUser.SOUS_COMMERCANT);
        utilisateurRepository.save(subUser);
        sous_commercant sousCommercant = new sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        pdvDuSousCommercant.setSousCommercant(sousCommercant);
        pdvRepository.save(pdvDuSousCommercant);
        sousCommercantRepository.save(sousCommercant);

        tpe tpeDuSousCommercant = new tpe();
        tpeDuSousCommercant.setNumeroSerie("TPE-SOUS-1");
        tpeDuSousCommercant.setPdv(pdvDuSousCommercant);
        tpeDuSousCommercant = tpeRepository.save(tpeDuSousCommercant);

        tpe tpeAutrePdv = new tpe();
        tpeAutrePdv.setNumeroSerie("TPE-AUTRE-1");
        tpeAutrePdv.setPdv(autrePdv);
        tpeAutrePdv = tpeRepository.save(tpeAutrePdv);

        Reclamation reclamationSousCommercant = new Reclamation();
        reclamationSousCommercant.setCommercant(commercantA);
        reclamationSousCommercant.setTpe(tpeDuSousCommercant);
        reclamationSousCommercant.setTypeProbleme("CONNECTIVITE");
        reclamationSousCommercant.setDescription("Probleme sur le PDV du sous-commerçant");
        reclamationSousCommercant.setStatut("EN_ATTENTE");
        reclamationSousCommercant.setPriorite("HAUTE");
        reclamationSousCommercant.setDateCreation(LocalDate.now());
        reclamationRepository.save(reclamationSousCommercant);

        Reclamation reclamationAutrePdv = new Reclamation();
        reclamationAutrePdv.setCommercant(commercantA);
        reclamationAutrePdv.setTpe(tpeAutrePdv);
        reclamationAutrePdv.setTypeProbleme("CONNECTIVITE");
        reclamationAutrePdv.setDescription("Probleme sur un AUTRE PDV du meme commercant");
        reclamationAutrePdv.setStatut("EN_ATTENTE");
        reclamationAutrePdv.setPriorite("HAUTE");
        reclamationAutrePdv.setDateCreation(LocalDate.now());
        reclamationRepository.save(reclamationAutrePdv);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/merchant/reclamations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(subUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.length()")
            .isEqualTo(1);
    }
}
