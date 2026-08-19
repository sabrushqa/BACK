package com.example.demo.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie la validation du flux public de soumission d'affiliation
 * (endpoint permitAll, pas d'authentification): champs obligatoires,
 * type invalide, doublon d'e-mail, documents obligatoires manquants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AffiliationRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);
    }

    private MultiValueMap<String, String> validPersonnePhysiqueTpeFormBase(String email) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("typeCommercant", "PP");
        form.add("typeAffiliation", "TPE");
        form.add("email", email);
        form.add("telephonePrincipal", "0600000000");
        form.add("activite", "Commerce general");
        form.add("secteur", "Commerce");
        form.add("adresse", "12 rue Test");
        form.add("ville", "Casablanca");
        form.add("region", "Casablanca-Settat");
        form.add("rib", "007123456789012345678901");
        form.add("nom", "Alaoui");
        form.add("prenom", "Youssef");
        form.add("cin", "AB123456");
        form.add("modeMiseADispositionTpe", "ACHAT");
        form.add("equipementTpe", "STANDARD");
        form.add("connectiviteTpe", "GPRS");
        form.add("nombreTpe", "1");
        return form;
    }

    @Test
    void rejectsSubmissionMissingRequiredField() {
        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.missingfield@test.lanacash.ma");
        form.remove("email");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsInvalidMerchantType() {
        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.invalidtype@test.lanacash.ma");
        form.set("typeCommercant", "TYPE_INEXISTANT");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsDuplicateEmail() {
        utilisateur existing = new utilisateur();
        existing.setEmail("affiliation.duplicate@test.lanacash.ma");
        existing.setRole(RoleUser.COMMERCANT);
        existing.setActive(true);
        existing.setDateCreation(LocalDate.now());
        utilisateurRepository.save(existing);

        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.duplicate@test.lanacash.ma");
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsSubmissionMissingRequiredDocuments() {
        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.missingdocs@test.lanacash.ma");
        // Ni fichier uploade ni *DocumentName renseigne pour cinDocument/ribDocument.

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsPointVenteCountMismatchWithDeclaredNombrePointsVente() {
        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.pdvmismatch@test.lanacash.ma");
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");
        form.add("nombrePointsVente", "2");
        form.add(
            "pointVentesJson",
            "[{\"nom\":\"Boutique Test\",\"adresse\":\"12 rue Test\",\"ville\":\"Casablanca\",\"telephone\":\"0600000000\"}]"
        );

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .extractingPath("$.message")
            .asString()
            .contains("nombre de fiches point de vente");
    }

    @Test
    void rejectsPointVenteMissingRequiredField() {
        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.pdvmissingfield@test.lanacash.ma");
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");
        form.add("nombrePointsVente", "1");
        form.add(
            "pointVentesJson",
            "[{\"adresse\":\"12 rue Test\",\"ville\":\"Casablanca\",\"telephone\":\"0600000000\"}]"
        );

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .extractingPath("$.message")
            .asString()
            .contains("nom");
    }

    @Test
    void rejectsMalformedPointVentesJson() {
        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.pdvmalformed@test.lanacash.ma");
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");
        form.add("nombrePointsVente", "1");
        form.add("pointVentesJson", "{not-valid-json");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .extractingPath("$.message")
            .asString()
            .contains("points de vente");
    }

    @Test
    void rejectsPointsVenteCountOutsideAllowedRange() {
        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.pdvoutofrange@test.lanacash.ma");
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");
        form.add("nombrePointsVente", "0");
        form.add("pointVentesJson", "[]");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .extractingPath("$.message")
            .asString()
            .contains("compris entre 1 et 10");
    }

    @Test
    void acceptsCompleteValidSubmission() {
        MultiValueMap<String, String> form = validPersonnePhysiqueTpeFormBase("affiliation.completesubmission@test.lanacash.ma");
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");
        form.add("nombrePointsVente", "1");
        form.add(
            "pointVentesJson",
            "[{\"nom\":\"Boutique Test\",\"adresse\":\"12 rue Test\",\"ville\":\"Casablanca\","
                + "\"telephone\":\"0600000000\",\"latitude\":33.5731,\"longitude\":-7.5898}]"
        );

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();

        assertThat(
            utilisateurRepository.existsByEmailIgnoreCase("affiliation.completesubmission@test.lanacash.ma")
        ).isTrue();
    }

    @Test
    void acceptsCompletePersonneMoraleSubmission() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("typeCommercant", "PM");
        form.add("typeAffiliation", "TPE");
        form.add("email", "affiliation.pm.complete@test.lanacash.ma");
        form.add("telephonePrincipal", "0600000001");
        form.add("activite", "Commerce de gros");
        form.add("secteur", "Commerce");
        form.add("adresse", "1 Avenue Hassan II");
        form.add("ville", "Casablanca");
        form.add("region", "Casablanca-Settat");
        form.add("rib", "007123456789012345678901");
        form.add("raisonSociale", "Lana Distribution SARL");
        form.add("rc", "RC-98765");
        form.add("ice", "ICE-555");
        form.add("formeJuridique", "SARL");
        form.add("representantLegal", "Nadia Fassi");
        form.add("modeMiseADispositionTpe", "ACHAT");
        form.add("equipementTpe", "STANDARD");
        form.add("connectiviteTpe", "GPRS");
        form.add("nombreTpe", "1");
        form.add("nombrePointsVente", "1");
        form.add(
            "pointVentesJson",
            "[{\"nom\":\"Boutique PM\",\"adresse\":\"1 Avenue Hassan II\",\"ville\":\"Casablanca\","
                + "\"telephone\":\"0600000001\"}]"
        );
        form.add("statutsDocumentName", "statuts.pdf");
        form.add("rcDocumentName", "rc.pdf");
        form.add("iceDocumentName", "ice.pdf");
        form.add("cinRepresentantDocumentName", "cin-representant.pdf");
        form.add("pvNominationDocumentName", "pv-nomination.pdf");
        form.add("ribDocumentName", "rib.pdf");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void acceptsCompleteAutoEntrepreneurSubmission() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("typeCommercant", "AE");
        form.add("typeAffiliation", "QRCODE");
        form.add("email", "affiliation.ae.complete@test.lanacash.ma");
        form.add("telephonePrincipal", "0600000002");
        form.add("activite", "Services aux particuliers");
        form.add("secteur", "Services");
        form.add("adresse", "2 rue des Fleurs");
        form.add("ville", "Rabat");
        form.add("region", "Rabat-Sale-Kenitra");
        form.add("rib", "007123456789012345678902");
        form.add("nom", "Chraibi");
        form.add("prenom", "Omar");
        form.add("numeroAutoEntrepreneur", "AE-7788");
        form.add("modeleQrSoftpos", "QR_STATIQUE");
        form.add("nombreQrSoftpos", "1");
        form.add("nombrePointsVente", "1");
        form.add(
            "pointVentesJson",
            "[{\"nom\":\"Kiosque AE\",\"adresse\":\"2 rue des Fleurs\",\"ville\":\"Rabat\","
                + "\"telephone\":\"0600000002\"}]"
        );
        form.add("cinDocumentName", "cin.pdf");
        form.add("attestationAeDocumentName", "attestation-ae.pdf");
        form.add("ribDocumentName", "rib.pdf");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void acceptsCompleteAssociationSubmission() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("typeCommercant", "ASSOCIATION");
        form.add("typeAffiliation", "ECOMMERCE");
        form.add("email", "affiliation.assoc.complete@test.lanacash.ma");
        form.add("telephonePrincipal", "0600000003");
        form.add("activite", "Aide sociale");
        form.add("secteur", "Associatif");
        form.add("adresse", "3 place de la Solidarite");
        form.add("ville", "Marrakech");
        form.add("region", "Marrakech-Safi");
        form.add("rib", "007123456789012345678903");
        form.add("nomEntite", "Association Solidarite Lana");
        form.add("representantLegal", "Samira Tazi");
        form.add("objet", "Aide sociale");
        form.add("modeServiceEcommerce", "SITE_MARCHAND");
        form.add("siteMarchandUrl", "https://solidarite-lana.example.ma");
        form.add("cinSignataireDocumentName", "cin-signataire.pdf");
        form.add("pvAssociationDocumentName", "pv-association.pdf");
        form.add("statutsDocumentName", "statuts.pdf");
        form.add("listeMembresDocumentName", "liste-membres.pdf");
        form.add("ribDocumentName", "rib.pdf");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void acceptsMultipartSubmissionWithRealUploadedDocuments() {
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        org.springframework.mock.web.MockMultipartFile cinFile = new org.springframework.mock.web.MockMultipartFile(
            "cinDocument", "cin.png", "image/png", pngBytes
        );
        org.springframework.mock.web.MockMultipartFile ribFile = new org.springframework.mock.web.MockMultipartFile(
            "ribDocument", "rib.png", "image/png", pngBytes
        );

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/affiliations")
                .file(cinFile)
                .file(ribFile)
                .param("typeCommercant", "PP")
                .param("typeAffiliation", "TPE")
                .param("email", "affiliation.multipart.complete@test.lanacash.ma")
                .param("telephonePrincipal", "0600000004")
                .param("activite", "Commerce general")
                .param("secteur", "Commerce")
                .param("adresse", "12 rue Test")
                .param("ville", "Casablanca")
                .param("region", "Casablanca-Settat")
                .param("rib", "007123456789012345678904")
                .param("nom", "Alaoui")
                .param("prenom", "Youssef")
                .param("cin", "AB998877")
                .param("modeMiseADispositionTpe", "ACHAT")
                .param("equipementTpe", "STANDARD")
                .param("connectiviteTpe", "GPRS")
                .param("nombreTpe", "1")
                .param("nombrePointsVente", "1")
                .param(
                    "pointVentesJson",
                    "[{\"nom\":\"Boutique Multipart\",\"adresse\":\"12 rue Test\",\"ville\":\"Casablanca\","
                        + "\"telephone\":\"0600000004\"}]"
                )
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void acceptsMultipartSubmissionWithJsonPayloadOverride() {
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        org.springframework.mock.web.MockMultipartFile cinFile = new org.springframework.mock.web.MockMultipartFile(
            "cinDocument", "cin.png", "image/png", pngBytes
        );
        org.springframework.mock.web.MockMultipartFile ribFile = new org.springframework.mock.web.MockMultipartFile(
            "ribDocument", "rib.png", "image/png", pngBytes
        );

        String payloadJson = """
            {
              "typeCommercant": "PP",
              "typeAffiliation": "TPE",
              "email": "affiliation.multipart.payload@test.lanacash.ma",
              "telephonePrincipal": "0600000005",
              "activite": "Commerce general",
              "secteur": "Commerce",
              "adresse": "12 rue Test",
              "ville": "Casablanca",
              "region": "Casablanca-Settat",
              "rib": "007123456789012345678905",
              "nom": "Alaoui",
              "prenom": "Youssef",
              "cin": "AB112233",
              "modeMiseADispositionTpe": "ACHAT",
              "equipementTpe": "STANDARD",
              "connectiviteTpe": "GPRS",
              "nombreTpe": "1",
              "nombrePointsVente": "1",
              "pointVentesJson": "[{\\"nom\\":\\"Boutique Payload\\",\\"adresse\\":\\"12 rue Test\\",\\"ville\\":\\"Casablanca\\",\\"telephone\\":\\"0600000005\\"}]"
            }
            """;

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/affiliations")
                .file(cinFile)
                .file(ribFile)
                .param("payload", payloadJson)
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void rejectsMultipartSubmissionWithInvalidJsonPayload() {
        org.springframework.mock.web.MockMultipartFile cinFile = new org.springframework.mock.web.MockMultipartFile(
            "cinDocument", "cin.png", "image/png", new byte[] {1, 2, 3}
        );

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/affiliations")
                .file(cinFile)
                .param("payload", "{ceci n'est pas du json valide}")
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void acceptsCompleteSoftposSubmission() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("typeCommercant", "PP");
        form.add("typeAffiliation", "SOFTPOS");
        form.add("email", "affiliation.softpos.complete@test.lanacash.ma");
        form.add("telephonePrincipal", "0600000006");
        form.add("activite", "Commerce general");
        form.add("secteur", "Commerce");
        form.add("adresse", "12 rue Test");
        form.add("ville", "Casablanca");
        form.add("region", "Casablanca-Settat");
        form.add("rib", "007123456789012345678906");
        form.add("nom", "Idrissi");
        form.add("prenom", "Salma");
        form.add("cin", "GH556677");
        form.add("modeleQrSoftpos", "STANDARD");
        form.add("nombreQrSoftpos", "1");
        form.add("nombrePointsVente", "1");
        form.add(
            "pointVentesJson",
            "[{\"nom\":\"Boutique SoftPOS\",\"adresse\":\"12 rue Test\",\"ville\":\"Casablanca\","
                + "\"telephone\":\"0600000006\"}]"
        );
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void acceptsCombinedEncaissementAndEcommerceSubmission() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("typeCommercant", "PP");
        form.add("typeAffiliation", "ENCAISSEMENTECOMMERCE");
        form.add("email", "affiliation.combined.complete@test.lanacash.ma");
        form.add("telephonePrincipal", "0600000007");
        form.add("activite", "Commerce general");
        form.add("secteur", "Commerce");
        form.add("adresse", "12 rue Test");
        form.add("ville", "Casablanca");
        form.add("region", "Casablanca-Settat");
        form.add("rib", "007123456789012345678907");
        form.add("nom", "Bennis");
        form.add("prenom", "Karim");
        form.add("cin", "IJ778899");
        form.add("modeMiseADispositionTpe", "ACHAT");
        form.add("equipementTpe", "STANDARD");
        form.add("connectiviteTpe", "GPRS");
        form.add("nombreTpe", "1");
        form.add("modeServiceEcommerce", "INTEGRATION_API");
        form.add("siteMarchandUrl", "https://boutique-combinee.example.ma");
        form.add("nombrePointsVente", "1");
        form.add(
            "pointVentesJson",
            "[{\"nom\":\"Boutique Combinee\",\"adresse\":\"12 rue Test\",\"ville\":\"Casablanca\","
                + "\"telephone\":\"0600000007\"}]"
        );
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void rejectsCombinedSubmissionWithoutTpeOrQrSoftposDetails() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("typeCommercant", "PP");
        form.add("typeAffiliation", "ENCAISSEMENTECOMMERCE");
        form.add("email", "affiliation.combined.incomplete@test.lanacash.ma");
        form.add("telephonePrincipal", "0600000008");
        form.add("activite", "Commerce general");
        form.add("secteur", "Commerce");
        form.add("adresse", "12 rue Test");
        form.add("ville", "Casablanca");
        form.add("region", "Casablanca-Settat");
        form.add("rib", "007123456789012345678908");
        form.add("nom", "Ziani");
        form.add("prenom", "Rachid");
        form.add("cin", "KL990011");
        form.add("modeServiceEcommerce", "INTEGRATION_API");
        form.add("siteMarchandUrl", "https://boutique-incomplete.example.ma");
        form.add("cinDocumentName", "cin.pdf");
        form.add("ribDocumentName", "rib.pdf");

        mvc.perform(
            MockMvcRequestBuilders.post("/api/affiliations")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .params(form)
        )
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void acceptsMultipartSubmissionForPersonneMoraleWithAllRealDocumentTypes() {
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/affiliations")
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "statutsDocument", "statuts.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "rcDocument", "rc.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "iceDocument", "ice.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "cinRepresentantDocument", "cin-representant.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "pvNominationDocument", "pv-nomination.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "ribDocument", "rib.png", "image/png", pngBytes
                ))
                .param("typeCommercant", "PM")
                .param("typeAffiliation", "TPE")
                .param("email", "affiliation.pm.realdocs@test.lanacash.ma")
                .param("telephonePrincipal", "0600000009")
                .param("activite", "Commerce de gros")
                .param("secteur", "Commerce")
                .param("adresse", "1 Avenue Hassan II")
                .param("ville", "Casablanca")
                .param("region", "Casablanca-Settat")
                .param("rib", "007123456789012345678909")
                .param("raisonSociale", "Lana Distribution PM Real SARL")
                .param("rc", "RC-11223")
                .param("ice", "ICE-334")
                .param("formeJuridique", "SARL")
                .param("representantLegal", "Nadia Fassi")
                .param("modeMiseADispositionTpe", "ACHAT")
                .param("equipementTpe", "STANDARD")
                .param("connectiviteTpe", "GPRS")
                .param("nombreTpe", "1")
                .param("nombrePointsVente", "1")
                .param(
                    "pointVentesJson",
                    "[{\"nom\":\"Boutique PM Real\",\"adresse\":\"1 Avenue Hassan II\",\"ville\":\"Casablanca\","
                        + "\"telephone\":\"0600000009\"}]"
                )
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void acceptsMultipartSubmissionForAssociationWithAllRealDocumentTypes() {
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/affiliations")
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "cinSignataireDocument", "cin-signataire.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "pvAssociationDocument", "pv-association.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "statutsDocument", "statuts.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "listeMembresDocument", "liste-membres.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "ribDocument", "rib.png", "image/png", pngBytes
                ))
                .param("typeCommercant", "ASSOCIATION")
                .param("typeAffiliation", "ECOMMERCE")
                .param("email", "affiliation.association.realdocs@test.lanacash.ma")
                .param("telephonePrincipal", "0600000010")
                .param("activite", "Aide sociale")
                .param("secteur", "Associatif")
                .param("adresse", "3 place de la Solidarite")
                .param("ville", "Marrakech")
                .param("region", "Marrakech-Safi")
                .param("rib", "007123456789012345678910")
                .param("nomEntite", "Association Solidarite Real")
                .param("representantLegal", "Samira Tazi")
                .param("objet", "Aide sociale")
                .param("modeServiceEcommerce", "SITE_MARCHAND")
                .param("siteMarchandUrl", "https://solidarite-real.example.ma")
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void acceptsMultipartSubmissionForAutoEntrepreneurWithAttestationDocument() {
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/affiliations")
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "cinDocument", "cin.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "attestationAeDocument", "attestation-ae.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "ribDocument", "rib.png", "image/png", pngBytes
                ))
                .param("typeCommercant", "AE")
                .param("typeAffiliation", "QRCODE")
                .param("email", "affiliation.ae.realdocs@test.lanacash.ma")
                .param("telephonePrincipal", "0600000011")
                .param("activite", "Services aux particuliers")
                .param("secteur", "Services")
                .param("adresse", "2 rue des Fleurs")
                .param("ville", "Rabat")
                .param("region", "Rabat-Sale-Kenitra")
                .param("rib", "007123456789012345678911")
                .param("nom", "Chraibi")
                .param("prenom", "Omar")
                .param("numeroAutoEntrepreneur", "AE-7788")
                .param("modeleQrSoftpos", "QR_STATIQUE")
                .param("nombreQrSoftpos", "1")
                .param("nombrePointsVente", "1")
                .param(
                    "pointVentesJson",
                    "[{\"nom\":\"Kiosque AE Real\",\"adresse\":\"2 rue des Fleurs\",\"ville\":\"Rabat\","
                        + "\"telephone\":\"0600000011\"}]"
                )
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void acceptsMultipartSubmissionForPersonnePhysiqueWithPatenteDocument() {
        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/affiliations")
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "cinDocument", "cin.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "ribDocument", "rib.png", "image/png", pngBytes
                ))
                .file(new org.springframework.mock.web.MockMultipartFile(
                    "patenteDocument", "patente.png", "image/png", pngBytes
                ))
                .param("typeCommercant", "PP")
                .param("typeAffiliation", "TPE")
                .param("email", "affiliation.pp.patente@test.lanacash.ma")
                .param("telephonePrincipal", "0600000012")
                .param("activite", "Commerce general")
                .param("secteur", "Commerce")
                .param("adresse", "12 rue Test")
                .param("ville", "Casablanca")
                .param("region", "Casablanca-Settat")
                .param("rib", "007123456789012345678912")
                .param("nom", "Alaoui")
                .param("prenom", "Youssef")
                .param("cin", "MN223344")
                .param("modeMiseADispositionTpe", "ACHAT")
                .param("equipementTpe", "STANDARD")
                .param("connectiviteTpe", "GPRS")
                .param("nombreTpe", "1")
                .param("nombrePointsVente", "1")
                .param(
                    "pointVentesJson",
                    "[{\"nom\":\"Boutique Patente\",\"adresse\":\"12 rue Test\",\"ville\":\"Casablanca\","
                        + "\"telephone\":\"0600000012\"}]"
                )
        )
            .assertThat()
            .hasStatus(HttpStatus.CREATED)
            .bodyJson()
            .extractingPath("$.dossierId")
            .isNotNull();
    }

    @Test
    void validatesDocumentAndReturnsSkippedWhenValidationDisabled() {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "cin.png", "image/png", new byte[] {1, 2, 3}
        );

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/affiliations/documents/validate")
                .file(file)
                .param("documentKey", "cinDocument")
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.status")
            .isEqualTo("SKIPPED");
    }
}
