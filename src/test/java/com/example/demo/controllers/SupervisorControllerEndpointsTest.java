package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import com.example.demo.services.SwitchMonetiqueClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce, via l'API HTTP reelle, une large partie des endpoints superviseur
 * dont la logique metier est deja testee au niveau service: carte des PDV,
 * stock TPE, et actions de gestion de compte (back-office/commerciale/
 * commercant).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class SupervisorControllerEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

    private MockMvcTester mvc;
    private utilisateur superviseur;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);
        superviseur = persistUser("superviseur.endpoints@test.lanacash.ma", RoleUser.SUPERVISEUR);
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

    private String authHeader() {
        return "Bearer " + tokenFor(superviseur);
    }

    @Test
    void getPdvMapReturnsOk() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/supervisor/pdvs/map")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void regeocoderPdvsReturnsOk() {
        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/pdvs/regeocoder")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void getTpeStockReturnsOk() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/supervisor/tpes")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void seedTpeStockEndpointIsNoLongerExposed() {
        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/tpes/seed")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void activateAndDeactivateTpeReturnOk() {
        String tpeId = "TPE-ENDPOINT-TEST-1";

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/tpes/" + tpeId + "/activate")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.OK);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/tpes/" + tpeId + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void deactivateBackOfficeReturnsOk() {
        utilisateur backOfficeUser = persistUser("bo.endpoint.deactivate@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice = backOfficeRepository.save(backOffice);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/back-offices/" + backOffice.getIdBackOffice() + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void deactivateCommercialeReturnsOk() {
        utilisateur commercialUser = persistUser("commercial.endpoint.deactivate@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/commerciales/" + commerciale.getIdCommercial() + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void deactivateCommercantReturnsOk() {
        utilisateur merchantUser = persistUser("commercant.endpoint.deactivate@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/commercants/" + commercant.getIdCommercant() + "/deactivate")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void changePasswordReturnsServiceUnavailableWhenKeycloakDisabled() {
        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/change-password")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Old1!\",\"newPassword\":\"New1!\",\"confirmPassword\":\"New1!\"}")
        ).assertThat().hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void getEligibleTpesForDossierReturnsOk() {
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Eligible Endpoint");
        commercant = commercantRepository.save(commercant);

        com.example.demo.entities.dossier_affiliation dossier = new com.example.demo.entities.dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(com.example.demo.enums.StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(com.example.demo.enums.TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        utilisateur backOfficeUser = persistUser("bo.endpoint.eligible@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutAffecterTpe(true);
        backOfficeRepository.save(backOffice);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/supervisor/tpes/eligible")
                .queryParam("dossierId", String.valueOf(dossier.getIdDossier()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void assignTpeToCommercantReturnsOk() {
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Assign Endpoint");
        commercant = commercantRepository.save(commercant);

        com.example.demo.entities.pdv pointVente = new com.example.demo.entities.pdv();
        pointVente.setCommercant(commercant);
        pdvRepository.save(pointVente);

        com.example.demo.entities.dossier_affiliation dossier = new com.example.demo.entities.dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(com.example.demo.enums.StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(com.example.demo.enums.TypeAffiliation.TPE);
        dossier.setNombreTpe(1);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        utilisateur backOfficeUser = persistUser("bo.endpoint.assigntpe@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutAffecterTpe(true);
        backOfficeRepository.save(backOffice);

        String tpeId = "TPE-ENDPOINT-ASSIGN-1";
        String merchantId = commercant.getIdCommercant().toString();
        String pointOfSaleId = pointVente.getIdPDV().toString();
        when(switchMonetiqueClient.parId(tpeId)).thenReturn(Optional.of(
            switchTpe(tpeId, "TPE", true, null, null)
        ));
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());
        when(switchMonetiqueClient.affecter(eq(tpeId), eq(merchantId), eq(pointOfSaleId), any(), any(), any()))
            .thenReturn(switchTpe(tpeId, "TPE", true, merchantId, pointOfSaleId));

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/tpes/" + tpeId + "/assign-commercant")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dossierId\":" + dossier.getIdDossier() + "}")
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void assignAffiliationToCommercialeReturnsOk() {
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Affiliation Endpoint");
        commercant.setRegion("Casablanca-Settat");
        commercant = commercantRepository.save(commercant);

        utilisateur commercialUser = persistUser("commercial.endpoint.assign@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setRegion("Casablanca-Settat");
        commerciale = commercialeRepository.save(commerciale);

        com.example.demo.entities.dossier_affiliation dossier = new com.example.demo.entities.dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(com.example.demo.enums.StatusDossier.EN_ATTENTE_ASSIGNATION);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.post("/api/supervisor/affiliations/" + dossier.getIdDossier() + "/assign")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"commercialeId\":" + commerciale.getIdCommercial() + "}")
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Autowired
    private com.example.demo.repositories.PdvRepository pdvRepository;

    @Autowired
    private com.example.demo.repositories.DossierAffiliationRepository dossierAffiliationRepository;

    private SwitchMonetiqueClient.SwitchTpe switchTpe(
        String id,
        String nature,
        boolean active,
        String merchantId,
        String pointOfSaleId
    ) {
        return new SwitchMonetiqueClient.SwitchTpe(
            id,
            merchantId,
            pointOfSaleId,
            nature,
            "4G",
            active,
            BigDecimal.ZERO,
            LocalDateTime.now()
        );
    }
}
