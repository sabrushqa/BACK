package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
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
 * Verifie que /api/auth/me renvoie la session du commercant authentifie
 * (jamais celle d'un autre) et refuse toute requete sans token valide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class AuthControllerCurrentSessionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    private MockMvcTester mvc;
    private utilisateur commercantUser;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);

        commercantUser = new utilisateur();
        commercantUser.setEmail("me.session.test@lanacash.ma");
        commercantUser.setRole(RoleUser.COMMERCANT);
        commercantUser.setActive(true);
        commercantUser.setDateCreation(LocalDate.now());
        commercantUser = utilisateurRepository.save(commercantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant.setNomCommercial("Boutique Session Test");
        commercantRepository.save(commercant);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void returnsAuthenticatedMerchantsOwnSession() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.email")
            .isEqualTo("me.session.test@lanacash.ma");
    }

    @Test
    void rejectsRequestWithoutAuthentication() {
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me"))
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsStaffWorkspaceSessionForSupervisor() {
        utilisateur superviseur = new utilisateur();
        superviseur.setEmail("me.session.superviseur@test.lanacash.ma");
        superviseur.setRole(com.example.demo.enums.RoleUser.SUPERVISEUR);
        superviseur.setActive(true);
        superviseur.setDateCreation(LocalDate.now());
        superviseur = utilisateurRepository.save(superviseur);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(superviseur))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.role")
            .isEqualTo("SUPERVISEUR");
    }

    @Test
    void returnsSubMerchantSessionForSousCommercant() {
        utilisateur subUser = new utilisateur();
        subUser.setEmail("me.session.souscommercant@test.lanacash.ma");
        subUser.setRole(com.example.demo.enums.RoleUser.SOUS_COMMERCANT);
        subUser.setActive(true);
        subUser.setDateCreation(LocalDate.now());
        subUser = utilisateurRepository.save(subUser);

        com.example.demo.entities.sous_commercant sousCommercant = new com.example.demo.entities.sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        sousCommercantRepository.save(sousCommercant);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(subUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.role")
            .isEqualTo("SOUS_COMMERCANT");
    }

    @Autowired
    private com.example.demo.repositories.SousCommercantRepository sousCommercantRepository;

    @Autowired
    private com.example.demo.repositories.BackOfficeRepository backOfficeRepository;

    @Autowired
    private com.example.demo.repositories.CommercialeRepository commercialeRepository;

    @Test
    void returnsStaffWorkspaceSessionForBackOfficeWithProfile() {
        utilisateur backOfficeUser = new utilisateur();
        backOfficeUser.setEmail("me.session.backoffice@test.lanacash.ma");
        backOfficeUser.setRole(com.example.demo.enums.RoleUser.BACK_OFFICE);
        backOfficeUser.setActive(true);
        backOfficeUser.setDateCreation(LocalDate.now());
        backOfficeUser = utilisateurRepository.save(backOfficeUser);

        com.example.demo.entities.back_office backOffice = new com.example.demo.entities.back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setNom("Alami");
        backOffice.setPrenom("Sara");
        backOffice.setService("Conformité");
        backOffice.setPeutValiderDossiers(true);
        backOffice.setPeutAffecterTpe(false);
        backOffice.setPeutGererReclamations(true);
        backOfficeRepository.save(backOffice);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.role")
            .isEqualTo("BACK_OFFICE");
    }

    @Test
    void returnsStaffWorkspaceSessionForCommercialWithProfile() {
        utilisateur commercialUser = new utilisateur();
        commercialUser.setEmail("me.session.commercial@test.lanacash.ma");
        commercialUser.setRole(com.example.demo.enums.RoleUser.COMMERCIAL);
        commercialUser.setActive(true);
        commercialUser.setDateCreation(LocalDate.now());
        commercialUser = utilisateurRepository.save(commercialUser);

        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setNom("Bennani");
        commerciale.setPrenom("Youssef");
        commerciale.setRegion("Casablanca-Settat");
        commerciale.setTelephone("0600000000");
        commercialeRepository.save(commerciale);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercialUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.role")
            .isEqualTo("COMMERCIAL");
    }

    @Autowired
    private com.example.demo.repositories.PdvRepository pdvRepository;

    @Autowired
    private com.example.demo.repositories.TpeRepository tpeRepository;

    @Autowired
    private com.example.demo.repositories.TransactionsRepository transactionsRepository;

    @Autowired
    private com.example.demo.repositories.SousCommercantRepository sousCommercantRepositoryForSession;

    @Autowired
    private com.example.demo.repositories.DossierAffiliationRepository dossierAffiliationRepository;

    @Test
    void returnsMerchantSessionWithPdvsTpesTransactionsAndSubMerchants() {
        com.example.demo.entities.dossier_affiliation dossier = new com.example.demo.entities.dossier_affiliation();
        dossier.setCommercant(commercantRepository.findByUtilisateur_Id(commercantUser.getId()).orElseThrow());
        dossier.setStatus(com.example.demo.enums.StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(com.example.demo.enums.TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        com.example.demo.entities.pdv pointVente = new com.example.demo.entities.pdv();
        pointVente.setCommercant(commercantRepository.findByUtilisateur_Id(commercantUser.getId()).orElseThrow());
        pointVente.setNomPDV("Point de vente principal");
        pointVente.setStatut("ACTIF");
        pointVente = pdvRepository.save(pointVente);

        utilisateur subUser = new utilisateur();
        subUser.setEmail("me.session.submerchant.data@test.lanacash.ma");
        subUser.setRole(com.example.demo.enums.RoleUser.SOUS_COMMERCANT);
        subUser.setActive(true);
        subUser.setDateCreation(LocalDate.now());
        subUser = utilisateurRepository.save(subUser);

        com.example.demo.entities.sous_commercant sousCommercant = new com.example.demo.entities.sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        sousCommercant.setNom("Idrissi");
        sousCommercant.setPrenom("Kenza");
        sousCommercant.setStatut("ACTIF");
        sousCommercant = sousCommercantRepositoryForSession.save(sousCommercant);
        pointVente.setSousCommercant(sousCommercant);
        pointVente = pdvRepository.save(pointVente);

        com.example.demo.entities.tpe terminal = new com.example.demo.entities.tpe();
        terminal.setNumeroSerie("TPE-SESSION-DATA-1");
        terminal.setPdv(pointVente);
        terminal.setStatut("ACTIF");
        terminal = tpeRepository.save(terminal);

        com.example.demo.entities.transactions transaction = new com.example.demo.entities.transactions();
        transaction.setTpe(terminal);
        transaction.setDateTransaction(LocalDate.now());
        transaction.setHeureTransaction(java.time.LocalTime.now());
        transaction.setMontant(new java.math.BigDecimal("150.50"));
        transaction.setDevise("MAD");
        transaction.setStatutTransaction("REUSSIE");
        transaction.setTypePaiement("CARTE");
        transactionsRepository.save(transaction);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.pdvs[0].nom")
            .isEqualTo("Point de vente principal");
    }

    @Test
    void returnsStaffWorkspaceSessionForBackOfficeWithoutProfileRow() {
        utilisateur backOfficeUser = new utilisateur();
        backOfficeUser.setEmail("me.session.backoffice.noprofile@test.lanacash.ma");
        backOfficeUser.setRole(com.example.demo.enums.RoleUser.BACK_OFFICE);
        backOfficeUser.setActive(true);
        backOfficeUser.setDateCreation(LocalDate.now());
        backOfficeUser = utilisateurRepository.save(backOfficeUser);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(backOfficeUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.role")
            .isEqualTo("BACK_OFFICE");
    }

    @Test
    void returnsStaffWorkspaceSessionForCommercialWithoutProfileRow() {
        utilisateur commercialUser = new utilisateur();
        commercialUser.setEmail("me.session.commercial.noprofile@test.lanacash.ma");
        commercialUser.setRole(com.example.demo.enums.RoleUser.COMMERCIAL);
        commercialUser.setActive(true);
        commercialUser.setDateCreation(LocalDate.now());
        commercialUser = utilisateurRepository.save(commercialUser);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercialUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.role")
            .isEqualTo("COMMERCIAL");
    }

    @Test
    void returnsSubMerchantSessionWithPdvsTpesAndTransactions() {
        utilisateur parentUser = new utilisateur();
        parentUser.setEmail("parent.submerchant.rich@test.lanacash.ma");
        parentUser.setRole(RoleUser.COMMERCANT);
        parentUser.setActive(true);
        parentUser.setDateCreation(LocalDate.now());
        parentUser = utilisateurRepository.save(parentUser);

        commercant parentCommercant = new commercant();
        parentCommercant.setUtilisateur(parentUser);
        parentCommercant.setNomCommercial("Boutique Parent Sous-Commercant");
        parentCommercant = commercantRepository.save(parentCommercant);

        com.example.demo.entities.dossier_affiliation dossier = new com.example.demo.entities.dossier_affiliation();
        dossier.setCommercant(parentCommercant);
        dossier.setStatus(com.example.demo.enums.StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(com.example.demo.enums.TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        utilisateur subUser = new utilisateur();
        subUser.setEmail("me.session.submerchant.rich@test.lanacash.ma");
        subUser.setRole(com.example.demo.enums.RoleUser.SOUS_COMMERCANT);
        subUser.setActive(true);
        subUser.setDateCreation(LocalDate.now());
        subUser = utilisateurRepository.save(subUser);

        com.example.demo.entities.sous_commercant sousCommercant = new com.example.demo.entities.sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        sousCommercant.setNom("Chraibi");
        sousCommercant.setPrenom("Nabil");
        sousCommercant.setStatut("ACTIF");
        sousCommercant = sousCommercantRepositoryForSession.save(sousCommercant);

        com.example.demo.entities.pdv pointVente = new com.example.demo.entities.pdv();
        pointVente.setCommercant(parentCommercant);
        pointVente.setSousCommercant(sousCommercant);
        pointVente.setNomPDV("Point de vente sous-commercant");
        pointVente.setVille("Rabat");
        pointVente.setStatut("ACTIF");
        pointVente = pdvRepository.save(pointVente);

        com.example.demo.entities.tpe terminal = new com.example.demo.entities.tpe();
        terminal.setNumeroSerie("TPE-SUBMERCHANT-RICH-1");
        terminal.setPdv(pointVente);
        terminal.setStatut("ACTIF");
        terminal = tpeRepository.save(terminal);

        com.example.demo.entities.transactions transaction = new com.example.demo.entities.transactions();
        transaction.setTpe(terminal);
        transaction.setDateTransaction(LocalDate.now());
        transaction.setHeureTransaction(java.time.LocalTime.now());
        transaction.setMontant(new java.math.BigDecimal("75.00"));
        transaction.setDevise("MAD");
        transaction.setStatutTransaction("REUSSIE");
        transaction.setTypePaiement("CARTE");
        transactionsRepository.save(transaction);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(subUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.pdvs[0].nom")
            .isEqualTo("Point de vente sous-commercant");
    }
}
