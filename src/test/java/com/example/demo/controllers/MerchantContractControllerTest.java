package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import com.example.demo.services.ServiceDocumentContratAffiliation;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test d'integration bout-en-bout: controller -> service -> repository -> SQL
 * Server reel, pour le chemin de consultation du contrat du commercant
 * authentifie (aucun ID client, resolu depuis le token).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class MerchantContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private ServiceDocumentContratAffiliation serviceDocumentContratAffiliation;

    private MockMvcTester mvc;
    private utilisateur commercantUser;
    private utilisateur commercantSansDossierUser;
    private Long dossierId;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);

        commercantUser = persistUser("commercant.contrat.test@lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant.setNomCommercial("Boutique Contrat Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        dossierId = dossier.getIdDossier();

        commercantSansDossierUser = persistUser("commercant.sans.dossier.test@lanacash.ma");
        commercant commercantSansDossier = new commercant();
        commercantSansDossier.setUtilisateur(commercantSansDossierUser);
        commercantRepository.save(commercantSansDossier);
    }

    private MockMultipartFile buildContractPdf(String filename, String bodyText) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(bodyText);
                contentStream.endText();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return new MockMultipartFile("file", filename, "application/pdf", outputStream.toByteArray());
        }
    }

    private MockMultipartFile buildSignedContractPdf(String filename, String bodyText) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(bodyText);
                contentStream.endText();

                // Zone adherent : X 0.02-0.42, Y (depuis le haut) 0.85-0.93, soit en
                // coordonnees PDF (origine en bas) Y entre 0.07*hauteur et 0.15*hauteur.
                float width = page.getMediaBox().getWidth();
                float height = page.getMediaBox().getHeight();
                float x0 = width * 0.05f;
                float x1 = width * 0.35f;
                float y0 = height * 0.08f;
                float y1 = height * 0.14f;
                contentStream.setNonStrokingColor(Color.BLACK);
                contentStream.addRect(x0, y0, x1 - x0, y1 - y0);
                contentStream.fill();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return new MockMultipartFile("file", filename, "application/pdf", outputStream.toByteArray());
        }
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
    void returnsLatestDossierStatusForAuthenticatedMerchant() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/commercant/contracts/latest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.dossierStatus")
            .isEqualTo("CONTRAT_A_SIGNER");
    }

    @Test
    void returnsNotFoundWhenMerchantHasNoDossier() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/commercant/contracts/latest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantSansDossierUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsRequestWithoutAuthentication() {
        mvc.perform(MockMvcRequestBuilders.get("/api/commercant/contracts/latest"))
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void downloadsLatestGeneratedContract() {
        dossier_affiliation dossier = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);
        dossier.setGeneratedContractPath(generated.cheminStocke());
        dossier.setGeneratedContractFileName(generated.nomFichier());
        dossierAffiliationRepository.save(dossier);

        mvc.perform(
            MockMvcRequestBuilders.get("/api/commercant/contracts/latest/download")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        ).assertThat().hasStatus(HttpStatus.OK);
    }

    @Test
    void verifiesSignatureAcceptsMatchingContractTemplate() throws Exception {
        MockMultipartFile contract = buildContractPdf(
            "contrat.pdf",
            "LANA CASH - Systeme de paiement - Cachet et signature de l'adherent"
        );

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/commercant/contracts/verify-signature")
                .file(contract)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.message")
            .asString()
            .contains("signature");
    }

    @Test
    void verifiesSignatureRejectsUnrelatedPdf() throws Exception {
        MockMultipartFile unrelated = buildContractPdf("document.pdf", "Ceci est un document quelconque.");

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/commercant/contracts/verify-signature")
                .file(unrelated)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.signed")
            .isEqualTo(false);
    }

    @Test
    void uploadsSignedContractAndFinalizesAcceptance() throws Exception {
        MockMultipartFile signedContract = buildSignedContractPdf(
            "contrat-signe.pdf",
            "LANA CASH - Systeme de paiement - Cachet et signature de l'adherent"
        );

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/commercant/contracts/latest/upload-signed")
                .file(signedContract)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        ).assertThat().hasStatus(HttpStatus.OK);

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThatDossierIsAccepted(reloaded);
    }

    @Test
    void rejectsUploadWhenSignatureZoneIsEmpty() throws Exception {
        // Meme template valide, mais sans rectangle rempli dans la zone de signature :
        // la reactivation du controle de signature doit rejeter ce depot.
        MockMultipartFile unsignedContract = buildContractPdf(
            "contrat-vierge.pdf",
            "LANA CASH - Systeme de paiement - Cachet et signature de l'adherent"
        );

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/commercant/contracts/latest/upload-signed")
                .file(unsignedContract)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(commercantUser))
        ).assertThat().hasStatus(HttpStatus.BAD_REQUEST);

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.CONTRAT_A_SIGNER);
    }

    private void assertThatDossierIsAccepted(dossier_affiliation dossier) {
        org.assertj.core.api.Assertions.assertThat(dossier.getStatus()).isEqualTo(StatusDossier.ACCEPTE);
    }
}
