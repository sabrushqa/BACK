package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Verifie que estFichierContratLanaCash accepte un vrai contrat Lana Cash
 * (mots-cles presents) et rejette un PDF quelconque sans ces mots-cles -
 * le chemin "accepte" n'etait teste que via des tests de securite qui ne
 * couvraient que les rejets.
 */
class ContratSignatureDetectorTest {

    private final ContratSignatureDetector contratSignatureDetector = new ContratSignatureDetector();

    private MockMultipartFile buildPdf(String filename, String bodyText) throws Exception {
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

    @Test
    void acceptsPdfContainingEncaissementContractKeywords() throws Exception {
        MockMultipartFile validContract = buildPdf(
            "contrat-signe.pdf",
            "LANA CASH - Systeme de paiement - Cachet et signature de l'adherent"
        );

        assertThat(contratSignatureDetector.estFichierContratLanaCash(validContract)).isTrue();
    }

    @Test
    void rejectsPdfWithoutExpectedKeywords() throws Exception {
        MockMultipartFile unrelatedPdf = buildPdf("document.pdf", "Ceci est un document quelconque sans rapport.");

        assertThat(contratSignatureDetector.estFichierContratLanaCash(unrelatedPdf)).isFalse();
    }

    @Test
    void rejectsFileWithoutPdfExtensionEvenWithMatchingContent() throws Exception {
        MockMultipartFile fakeExtension = buildPdf(
            "contrat-signe.exe",
            "LANA CASH - Systeme de paiement - Cachet et signature de l'adherent"
        );

        assertThat(contratSignatureDetector.estFichierContratLanaCash(fakeExtension)).isFalse();
    }

    @Test
    void emptySignatureZoneIsNotDetectedAsSigned() throws Exception {
        MockMultipartFile validContract = buildPdf(
            "contrat-signe.pdf",
            "LANA CASH - Systeme de paiement - Cachet et signature de l'adherent"
        );

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(validContract)).isFalse();
    }

    @Test
    void printedSignatureLabelAndEmptyBoxAreNotMistakenForSignature() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
                contentStream.newLineAtOffset(width * 0.05f, height * 0.14f);
                contentStream.showText("Cachet et signature de l'adherent");
                contentStream.endText();
                contentStream.addRect(
                    width * 0.048f,
                    height * 0.067f,
                    width * 0.447f,
                    height * 0.055f
                );
                contentStream.stroke();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            MockMultipartFile blankGeneratedContract = new MockMultipartFile(
                "file",
                "contrat-affiliation-vierge.pdf",
                "application/pdf",
                outputStream.toByteArray()
            );

            assertThat(contratSignatureDetector.estZoneSignatureRemplie(blankGeneratedContract)).isFalse();
        }
    }

    @Test
    void filledSignatureZoneIsDetectedAsSigned() throws Exception {
        MockMultipartFile signedContract = buildPdfWithFilledSignatureZone();

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(signedContract)).isTrue();
    }

    private MockMultipartFile buildPdfWithFilledSignatureZone() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();
            // Zone adherent en bas-gauche : X 0.02-0.42, Y (depuis le haut) 0.85-0.93
            // soit, en coordonnees PDF (origine en bas), Y entre 0.07*hauteur et 0.15*hauteur.
            float x0 = width * 0.05f;
            float x1 = width * 0.35f;
            float y0 = height * 0.08f;
            float y1 = height * 0.14f;
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 10);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Cachet et signature de l'adherent");
                contentStream.endText();
                contentStream.setNonStrokingColor(Color.BLACK);
                contentStream.addRect(x0, y0, x1 - x0, y1 - y0);
                contentStream.fill();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return new MockMultipartFile("file", "contrat-signe.pdf", "application/pdf", outputStream.toByteArray());
        }
    }

    @Test
    void detectsSignatureFromUploadedImage() throws Exception {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 400, 300);
        graphics.setColor(Color.BLACK);
        // Zone adherent : X 0.02-0.42, Y 0.85-0.93 (depuis le haut de l'image).
        graphics.fillRect((int) (400 * 0.05), (int) (300 * 0.87), (int) (400 * 0.30), (int) (300 * 0.04));
        graphics.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        MockMultipartFile signedImage =
            new MockMultipartFile("file", "signature.png", "image/png", outputStream.toByteArray());

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(signedImage)).isTrue();
    }

    @Test
    void emptyImageSignatureZoneIsNotDetectedAsSigned() throws Exception {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 400, 300);
        graphics.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        MockMultipartFile blankImage =
            new MockMultipartFile("file", "signature.png", "image/png", outputStream.toByteArray());

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(blankImage)).isFalse();
    }

    @Test
    void unreadableImageIsRejected() throws Exception {
        MockMultipartFile corrupt =
            new MockMultipartFile("file", "signature.png", "image/png", "not-a-real-image".getBytes());

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(corrupt)).isFalse();
    }

    @Test
    void unreadablePdfIsRejectedFailClosed() {
        MockMultipartFile corrupt = new MockMultipartFile(
            "file", "contrat.pdf", "application/pdf", "not-a-real-pdf".getBytes()
        );

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(corrupt)).isFalse();
    }

    private void addSignedPage(PDDocument document, boolean signed) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        float width = page.getMediaBox().getWidth();
        float height = page.getMediaBox().getHeight();
        float x0 = width * 0.05f;
        float x1 = width * 0.35f;
        float y0 = height * 0.08f;
        float y1 = height * 0.14f;
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 10);
            contentStream.newLineAtOffset(50, 700);
            contentStream.showText("Cachet et signature de l'adherent");
            contentStream.endText();
            if (!signed) {
                return;
            }
            contentStream.setNonStrokingColor(Color.BLACK);
            contentStream.addRect(x0, y0, x1 - x0, y1 - y0);
            contentStream.fill();
        }
    }

    private MockMultipartFile buildMultiPagePdf(boolean... pagesSignees) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (boolean signed : pagesSignees) {
                addSignedPage(document, signed);
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return new MockMultipartFile("file", "contrats-fusionnes.pdf", "application/pdf", outputStream.toByteArray());
        }
    }

    /**
     * Un dossier combine (ENCAISSEMENT_ET_ECOMMERCE) fusionne plusieurs sous-contrats,
     * chacun avec sa propre zone de signature sur sa propre derniere page. Verifier
     * uniquement la derniere page du PDF fusionne (ancien comportement) ne detecterait
     * que le dernier sous-contrat et accepterait a tort un contrat partiellement signe.
     */
    @Test
    void rejectsMultiSectionContractWhenOnlyOneOfTwoSectionsIsSigned() throws Exception {
        MockMultipartFile file = buildMultiPagePdf(true, false);

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(file, 2)).isFalse();
    }

    @Test
    void acceptsMultiSectionContractWhenAllSectionsAreSigned() throws Exception {
        MockMultipartFile file = buildMultiPagePdf(true, true);

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(file, 2)).isTrue();
    }

    @Test
    void rejectsMultiSectionContractWhenItContainsUnexpectedExtraSection() throws Exception {
        MockMultipartFile file = buildMultiPagePdf(true, true, true);

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(file, 2)).isFalse();
    }

    @Test
    void singleArgumentOverloadRequiresOnlyOneSignedSection() throws Exception {
        MockMultipartFile file = buildPdfWithFilledSignatureZone();

        assertThat(contratSignatureDetector.estZoneSignatureRemplie(file)).isTrue();
    }
}
