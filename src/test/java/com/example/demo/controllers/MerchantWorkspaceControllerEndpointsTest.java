package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.sous_commercant;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce, via l'API HTTP reelle, l'activation/desactivation de sous-commercant
 * et l'affectation d'un TPE a un point de vente pour l'espace commercant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class MerchantWorkspaceControllerEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private SousCommercantRepository sousCommercantRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);
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
    void activateAndDeactivateSubMerchantReturnOk() {
        utilisateur merchantUser = persistUser("commercant.endpoint.submerchant@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        utilisateur subUser = persistUser("sous.commercant.endpoint@test.lanacash.ma");
        subUser.setActive(false);
        utilisateurRepository.save(subUser);

        sous_commercant sousCommercant = new sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        sousCommercant.setCommercant(commercant);
        sousCommercant = sousCommercantRepository.save(sousCommercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente.setSousCommercant(sousCommercant);
        pdvRepository.save(pointVente);

        mvc.perform(
            MockMvcRequestBuilders.post(
                "/api/commercant/workspace/sub-merchants/" + sousCommercant.getIdSousCommercant() + "/activate"
            ).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(merchantUser))
        ).assertThat().hasStatus(HttpStatus.OK);

        mvc.perform(
            MockMvcRequestBuilders.post(
                "/api/commercant/workspace/sub-merchants/" + sousCommercant.getIdSousCommercant() + "/deactivate"
            ).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(merchantUser))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void assignTpeToPdvReturnsOk() {
        utilisateur merchantUser = persistUser("commercant.endpoint.assigntpe@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        tpe terminal = new tpe();
        terminal.setNumeroSerie("TPE-ENDPOINT-ASSIGN-1");
        terminal.setPdv(pointVente);
        terminal = tpeRepository.save(terminal);

        pdv pointVenteCible = new pdv();
        pointVenteCible.setCommercant(commercant);
        pointVenteCible = pdvRepository.save(pointVenteCible);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/commercant/workspace/tpes/" + terminal.getIdTPE() + "/pdv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(merchantUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pdvId\":" + pointVenteCible.getIdPDV() + "}")
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void moveSubMerchantToPdvReturnsOk() {
        utilisateur merchantUser = persistUser("commercant.endpoint.movesubmerchant@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        utilisateur subUser = persistUser("sous.commercant.move.endpoint@test.lanacash.ma");
        subUser.setActive(false);
        utilisateurRepository.save(subUser);

        sous_commercant sousCommercant = new sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        sousCommercant.setCommercant(commercant);
        sousCommercant = sousCommercantRepository.save(sousCommercant);

        pdv pointVenteActuel = new pdv();
        pointVenteActuel.setCommercant(commercant);
        pointVenteActuel.setSousCommercant(sousCommercant);
        pdvRepository.save(pointVenteActuel);

        pdv pointVenteCible = new pdv();
        pointVenteCible.setCommercant(commercant);
        pointVenteCible = pdvRepository.save(pointVenteCible);

        mvc.perform(
            MockMvcRequestBuilders.post(
                "/api/commercant/workspace/sub-merchants/" + sousCommercant.getIdSousCommercant() + "/pdv"
            )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(merchantUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pdvId\":" + pointVenteCible.getIdPDV() + "}")
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.pdvId")
            .isEqualTo(pointVenteCible.getIdPDV().intValue());
    }

    @Test
    void requestNewPdvProductReturnsOk() {
        utilisateur merchantUser = persistUser("commercant.endpoint.newpdv@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation acceptedDossier = new dossier_affiliation();
        acceptedDossier.setCommercant(commercant);
        acceptedDossier.setStatus(StatusDossier.ACCEPTE);
        acceptedDossier.setTypeAffiliation(TypeAffiliation.TPE);
        acceptedDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(acceptedDossier);

        String body = """
            {"nom":"Nouveau PDV Endpoint","adresse":"12 rue Test","ville":"Casablanca",
             "telephone":"0600000000","typeAffiliation":"TPE","latitude":33.5731,"longitude":-7.5898}
            """;

        mvc.perform(
            MockMvcRequestBuilders.post("/api/commercant/workspace/pdvs/product-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(merchantUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).assertThat().hasStatus(HttpStatus.OK);
    }
}
