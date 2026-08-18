package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.entities.commerciale;
import com.example.demo.entities.dossier_affiliation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceDocumentContratAffiliationTest {

    @TempDir
    Path tempDirectory;

    @Test
    void generatedCommercialReportFitsSingleA4PageAndCanBeDownloaded() throws IOException {
        GenerateurModeleContratAffiliation templateRenderer =
            new GenerateurModeleContratAffiliation(null, null, null, null, new PdfLogoProvider());
        ServiceDocumentContratAffiliation documentService =
            new ServiceDocumentContratAffiliation(
                templateRenderer,
                null,
                tempDirectory.toString(),
                false,
                ""
            );

        dossier_affiliation dossier = buildCommercialReportDossier();

        ServiceDocumentContratAffiliation.CompteRenduCommercialGenere generatedReport =
            documentService.genererCompteRenduCommercial(dossier);

        Path storedReportPath = tempDirectory
            .resolve("dossier-" + dossier.getIdDossier())
            .resolve(generatedReport.nomFichier());

        assertTrue(Files.exists(storedReportPath));
        assertEquals(generatedReport.cheminStocke(), storedReportPath.toString());

        try (PDDocument generatedPdf = PDDocument.load(Files.readAllBytes(storedReportPath))) {
            assertEquals(1, generatedPdf.getNumberOfPages());
        }

        ServiceDocumentContratAffiliation.ContratTelecharge downloadedReport =
            documentService.telechargerFichier(generatedReport.cheminStocke());

        assertEquals(generatedReport.nomFichier(), downloadedReport.nomFichier());
        assertEquals("application/pdf", downloadedReport.typeContenu());

        try (PDDocument downloadedPdf = PDDocument.load(downloadedReport.contenu())) {
            assertEquals(1, downloadedPdf.getNumberOfPages());
        }
    }

    private dossier_affiliation buildCommercialReportDossier() {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setIdDossier(42L);
        dossier.setCompteRenduQualification("AFFILIE");
        dossier.setCompteRenduAcquereur(
            "CIH Bank - portefeuille historique avec besoin de reprise du flux monetique et accompagnement contractuel."
        );
        dossier.setCompteRenduOrigineProspect("APPORTEUR_AFFAIRES");
        dossier.setCompteRenduOrigineProspectDetail("Partenaire terrain regional");
        dossier.setCompteRenduContactNomPrenom("Nadia Benali");
        dossier.setCompteRenduContactFonction("Gerante");
        dossier.setCompteRenduPointVenteAcronyme("NB STORE");
        dossier.setCompteRenduActionnaires("Nadia Benali, Youssef Benali");
        dossier.setCompteRenduCommercant("Nadia Lifestyle Store");
        dossier.setCompteRenduChaine("Lifestyle Market");
        dossier.setCompteRenduRelationsLc(
            repeatedSentence(
                "Relations commerciales existantes avec plusieurs points de vente LC dans la meme zone de chalandise.",
                4
            )
        );
        dossier.setCompteRenduDateOuverture("2025-01-12");
        dossier.setCompteRenduNombreEmployes("18");
        dossier.setCompteRenduActivite("Pret-a-porter et accessoires");
        dossier.setCompteRenduMcc("5691");
        dossier.setCompteRenduStandingMagasin("Premium");
        dossier.setCompteRenduNatureMarchandises(
            repeatedSentence(
                "Vente de vetements, chaussures, accessoires et services d'emballage premium pour cadeaux.",
                5
            )
        );
        dossier.setCompteRenduSuperficieLocal("ENTRE_30_ET_100_M2");
        dossier.setCompteRenduStatutLocal("LOUE");
        dossier.setCompteRenduChiffreAffairesAnnuel("DE_1_A_5_MDHS");
        dossier.setCompteRenduPartPaiementCarte("67");
        dossier.setCompteRenduPartCarteLocale("54");
        dossier.setCompteRenduProfilCommercant("IN_SHORE_VIREMENT");
        dossier.setCompteRenduAppreciationVisite("FAVORABLE");
        dossier.setCompteRenduCommentaire(
            repeatedSentence(
                "Visite concluante avec un bon niveau d'organisation, un flux client regulier et une forte appetence pour les paiements cartes.",
                6
            )
        );
        dossier.setCompteRenduFaitA("Casablanca");
        dossier.setCompteRenduDateVisite("2026-04-24");

        commerciale commerciale = new commerciale();
        commerciale.setPrenom("Sanae");
        commerciale.setNom("Alaoui");
        dossier.setCommerciale(commerciale);

        return dossier;
    }

    private String repeatedSentence(String sentence, int repetitions) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < repetitions; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(sentence);
        }
        return builder.toString();
    }
}
