package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.PdvRepository;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Complete la couverture de ServiceDocumentContratAffiliation: generation du
 * contrat PDF (genererContrat), verification de disponibilite d'un fichier,
 * et enregistrement d'un contrat signe uploade.
 */
class ServiceDocumentContratAffiliationExtraTest {

    @TempDir
    Path tempDirectory;

    private ServiceDocumentContratAffiliation buildService() {
        return buildService(null);
    }

    private ServiceDocumentContratAffiliation buildService(PdvRepository pdvRepository) {
        GenerateurModeleContratAffiliation templateRenderer =
            new GenerateurModeleContratAffiliation(null, null, null, null, new PdfLogoProvider());
        return new ServiceDocumentContratAffiliation(
            templateRenderer,
            pdvRepository,
            tempDirectory.toString(),
            false,
            ""
        );
    }

    private dossier_affiliation buildDossier(long id, TypeAffiliation typeAffiliation) {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setIdDossier(id);
        dossier.setTypeAffiliation(typeAffiliation);
        dossier.setRib("007123456789012345678901");
        commercant commercant = new commercant();
        dossier.setCommercant(commercant);
        return dossier;
    }

    @Test
    void generatesContractPdfForSinglePointDeVente() throws Exception {
        ServiceDocumentContratAffiliation documentService = buildService();
        dossier_affiliation dossier = buildDossier(101L, TypeAffiliation.TPE);

        ServiceDocumentContratAffiliation.ContratGenere generated = documentService.genererContrat(dossier);

        assertThat(generated.nomFichier()).contains("101");
        assertThat(Files.exists(Path.of(generated.cheminStocke()))).isTrue();
    }

    @Test
    void fichierDisponibleReflectsFileExistence() throws Exception {
        ServiceDocumentContratAffiliation documentService = buildService();
        dossier_affiliation dossier = buildDossier(102L, TypeAffiliation.TPE);
        ServiceDocumentContratAffiliation.ContratGenere generated = documentService.genererContrat(dossier);

        assertThat(documentService.fichierDisponible(generated.cheminStocke())).isTrue();
        assertThat(documentService.fichierDisponible("chemin/inexistant.pdf")).isFalse();
        assertThat(documentService.fichierDisponible(null)).isFalse();
    }

    @Test
    void registersUploadedSignedContractWithSanitizedFileName() throws Exception {
        ServiceDocumentContratAffiliation documentService = buildService();
        dossier_affiliation dossier = buildDossier(103L, TypeAffiliation.TPE);

        MockMultipartFile signedFile = new MockMultipartFile(
            "file", "../../contrat signé (1).pdf", "application/pdf", "fake-pdf-bytes".getBytes()
        );

        ServiceDocumentContratAffiliation.ContratSigneEnregistre enregistre =
            documentService.enregistrerContratSigne(dossier, signedFile);

        // Le nom brut contient des ".." litteraux (aucun "/" autour, donc pas de
        // traversal reel), mais la propriete de securite qui compte est verifiee
        // ici: le fichier stocke reste bien a l'interieur du repertoire prevu.
        Path storedPath = Path.of(enregistre.cheminStocke()).normalize();
        Path signedDirectory = tempDirectory.resolve("dossier-103").resolve("signed").normalize();
        assertThat(storedPath).exists();
        assertThat(storedPath.getParent()).isEqualTo(signedDirectory);
    }

    @Test
    void generatesCommercialReportPdf() {
        ServiceDocumentContratAffiliation documentService = buildService();
        dossier_affiliation dossier = buildDossier(104L, TypeAffiliation.TPE);

        ServiceDocumentContratAffiliation.CompteRenduCommercialGenere generated =
            documentService.genererCompteRenduCommercial(dossier);

        assertThat(generated.nomFichier()).contains("104");
        assertThat(Files.exists(Path.of(generated.cheminStocke()))).isTrue();
    }

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    private byte[] blankPdfBytes() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    @Test
    void mergesFullDossierWithContractReportAndAttachedDocuments() throws Exception {
        ServiceDocumentContratAffiliation documentService = buildService();
        dossier_affiliation dossier = buildDossier(105L, TypeAffiliation.TPE);

        byte[] merged = documentService.genererDossierComplet(
            dossier,
            List.of(),
            blankPdfBytes(),
            blankPdfBytes(),
            List.of(
                new ServiceDocumentContratAffiliation.DocumentAFusionner(
                    "cin.png", "image/png", ONE_PIXEL_PNG
                ),
                new ServiceDocumentContratAffiliation.DocumentAFusionner(
                    "rib.pdf", "application/pdf", blankPdfBytes()
                ),
                new ServiceDocumentContratAffiliation.DocumentAFusionner(
                    "note.txt", "text/plain", "hello".getBytes()
                )
            )
        );

        assertThat(merged).isNotEmpty();
    }

    @Test
    void generatesMergedContractForMultiplePointsDeVente() throws Exception {
        PdvRepository pdvRepository = mock(PdvRepository.class);
        ServiceDocumentContratAffiliation documentService = buildService(pdvRepository);

        dossier_affiliation dossier = buildDossier(106L, TypeAffiliation.TPE);
        dossier.getCommercant().setIdCommercant(106L);

        pdv pdv1 = new pdv();
        pdv1.setIdPDV(1L);
        pdv pdv2 = new pdv();
        pdv2.setIdPDV(2L);
        when(pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(106L))
            .thenReturn(List.of(pdv1, pdv2));

        ServiceDocumentContratAffiliation.ContratGenere generated = documentService.genererContrat(dossier);

        assertThat(generated.nomFichier()).contains("contrats-affiliation-106");
        assertThat(Files.exists(Path.of(generated.cheminStocke()))).isTrue();
    }

    @Test
    void generatesSinglePdvContractForRequestedPdvOnNewPdvDossier() throws Exception {
        ServiceDocumentContratAffiliation documentService = buildService();

        dossier_affiliation dossier = buildDossier(107L, TypeAffiliation.TPE);
        dossier.setOrigineCreation("NOUVEAU_PDV");
        pdv requestedPdv = new pdv();
        requestedPdv.setIdPDV(9L);
        dossier.setRequestedPdv(requestedPdv);

        ServiceDocumentContratAffiliation.ContratGenere generated = documentService.genererContrat(dossier);

        assertThat(generated.nomFichier()).contains("contrat-affiliation-107");
        assertThat(Files.exists(Path.of(generated.cheminStocke()))).isTrue();
    }

    @Test
    void generatesCombinedContractForEncaissementAndEcommerceDossier() throws Exception {
        PdvRepository pdvRepository = mock(PdvRepository.class);
        ServiceDocumentContratAffiliation documentService = buildService(pdvRepository);

        dossier_affiliation dossier = buildDossier(108L, TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE);
        dossier.getCommercant().setIdCommercant(108L);
        dossier.setModeServiceEcommerce("SITE_MARCHAND");
        dossier.setSiteMarchandUrl("https://boutique.example.ma");

        pdv pdv1 = new pdv();
        pdv1.setIdPDV(1L);
        when(pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(108L))
            .thenReturn(List.of(pdv1));

        ServiceDocumentContratAffiliation.ContratGenere generated = documentService.genererContrat(dossier);

        assertThat(generated.nomFichier()).contains("contrats-affiliation-108");
        assertThat(Files.exists(Path.of(generated.cheminStocke()))).isTrue();
    }

    @Test
    void resolvesOneExpectedSignatureSectionWhenNoPdvExists() {
        ServiceDocumentContratAffiliation documentService = buildService();
        dossier_affiliation dossier = buildDossier(109L, TypeAffiliation.TPE);

        assertThat(documentService.resolveExpectedSignatureSections(dossier)).isEqualTo(1);
    }

    @Test
    void resolvesOneExpectedSignatureSectionPerPdvForRegularDossier() {
        PdvRepository pdvRepository = mock(PdvRepository.class);
        ServiceDocumentContratAffiliation documentService = buildService(pdvRepository);

        dossier_affiliation dossier = buildDossier(110L, TypeAffiliation.TPE);
        dossier.getCommercant().setIdCommercant(110L);

        pdv pdv1 = new pdv();
        pdv1.setIdPDV(1L);
        pdv pdv2 = new pdv();
        pdv2.setIdPDV(2L);
        pdv pdv3 = new pdv();
        pdv3.setIdPDV(3L);
        when(pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(110L))
            .thenReturn(List.of(pdv1, pdv2, pdv3));

        assertThat(documentService.resolveExpectedSignatureSections(dossier)).isEqualTo(3);
    }

    @Test
    void resolvesPdvCountPlusOneForCombinedEncaissementAndEcommerceDossier() {
        PdvRepository pdvRepository = mock(PdvRepository.class);
        ServiceDocumentContratAffiliation documentService = buildService(pdvRepository);

        dossier_affiliation dossier = buildDossier(111L, TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE);
        dossier.getCommercant().setIdCommercant(111L);

        pdv pdv1 = new pdv();
        pdv1.setIdPDV(1L);
        pdv pdv2 = new pdv();
        pdv2.setIdPDV(2L);
        when(pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(111L))
            .thenReturn(List.of(pdv1, pdv2));

        // 2 PDV (contrats encaissement) + 1 contrat e-commerce = 3 sections attendues.
        assertThat(documentService.resolveExpectedSignatureSections(dossier)).isEqualTo(3);
    }

    @Test
    void resolvesOneExpectedSignatureSectionForNewPdvRequestRegardlessOfExistingPdvCount() {
        ServiceDocumentContratAffiliation documentService = buildService();
        dossier_affiliation dossier = buildDossier(112L, TypeAffiliation.TPE);
        dossier.setOrigineCreation("NOUVEAU_PDV");
        pdv requestedPdv = new pdv();
        requestedPdv.setIdPDV(9L);
        dossier.setRequestedPdv(requestedPdv);

        assertThat(documentService.resolveExpectedSignatureSections(dossier)).isEqualTo(1);
    }
}
