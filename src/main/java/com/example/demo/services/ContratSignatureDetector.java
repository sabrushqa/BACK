package com.example.demo.services;

import com.example.demo.enums.TypeAffiliation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Valide et analyse les contrats d'affiliation LANA CASH uploadés.
 * Deux vérifications distinctes :
 *   1. estFichierContratLanaCash  – s'assure que le PDF est bien un contrat Lana Cash
 *   2. estZoneSignatureRemplie    – détecte si la zone de signature adhérent est signée
 */
@Service
public class ContratSignatureDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContratSignatureDetector.class);

    // Le template "encaissement" (TPE, SoftPOS, QR Code — GenerateurModeleContratAffiliation
    // .genererHtmlContratEncaissement) est écrit sans accents ("Systeme", "adherent"...).
    // Le template "ecommerce" (E_COMMERCE uniquement) est lui accentué. Les deux variantes
    // doivent être reconnues : un contrat TPE n'a jamais "e-commerce" dans son texte, et ses
    // mots-clés n'ont jamais d'accents, donc une seule liste commune ne peut pas matcher les deux.
    private static final List<String> MOTS_CLES_ENCAISSEMENT = List.of(
        "lana cash",
        "systeme de paiement",
        "cachet et signature de l'adherent"
    );

    private static final List<String> MOTS_CLES_ECOMMERCE = List.of(
        "lana cash",
        "système de paiement e-commerce",
        "cachet et signature de l'adhérent"
    );

    // Intérieur du rectangle de signature adhérent (bas-gauche), exprimé en
    // fraction de la page. Ne jamais inclure le libellé imprimé ni les bordures :
    // sur le vrai PDF généré, l'ancienne zone Y=0.85..0.93 contenait le texte
    // "Cachet et Signature..." et produisait 3 % de pixels foncés dans un contrat
    // totalement vierge, au-dessus du seuil de validation de 1 %.
    private static final double ZONE_X_START = 0.055;
    private static final double ZONE_X_END   = 0.48;
    private static final double ZONE_Y_START = 0.885;
    private static final double ZONE_Y_END   = 0.925;

    // Un pixel est considéré "foncé" si sa luminosité moyenne < ce seuil (0-255)
    private static final int BRIGHTNESS_THRESHOLD = 100;

    // Ratio minimum de pixels foncés dans la zone pour valider la présence d'une signature.
    // Zone vide mesurée à < 0.3 % (bruit d'antialiasing uniquement). Un trait de signature
    // fin (tracé au trackpad/doigt, sans cachet encré) mesure déjà ~1.8 %. Seuil à 1 %
    // pour accepter une signature réelle, même fine, tout en rejetant une zone vide.
    private static final double DARK_PIXEL_THRESHOLD = 0.01;

    private static final float RENDER_DPI = 150f;

    /**
     * Vérifie que le fichier est bien un PDF correspondant au template contrat d'affiliation
     * Lana Cash. Rejette tout autre fichier (image, autre document PDF, etc.).
     */
    public boolean estFichierContratLanaCash(MultipartFile file) {
        return estFichierContratLanaCash(file, null);
    }

    /**
     * @param typeAffiliation si ENCAISSEMENT_ET_ECOMMERCE, le fichier fusionné doit contenir
     *                        les DEUX jeux de mots-clés (dossier combiné = un seul fichier avec
     *                        tous les contrats). Pour tout autre type (ou null), un seul des deux
     *                        jeux suffit, comme avant.
     */
    public boolean estFichierContratLanaCash(MultipartFile file, TypeAffiliation typeAffiliation) {
        try {
            String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
            if (!filename.endsWith(".pdf")) {
                LOGGER.warn("Fichier rejeté : n'est pas un PDF ({})", filename);
                return false;
            }
            byte[] bytes = file.getBytes();
            try (PDDocument document = PDDocument.load(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document).toLowerCase();
                boolean contientEncaissement = MOTS_CLES_ENCAISSEMENT.stream().allMatch(text::contains);
                boolean contientEcommerce = MOTS_CLES_ECOMMERCE.stream().allMatch(text::contains);
                boolean valid = typeAffiliation == TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE
                    ? contientEncaissement && contientEcommerce
                    : contientEncaissement || contientEcommerce;
                if (!valid) {
                    LOGGER.warn(
                        "Fichier rejeté : ne correspond pas au template contrat d'affiliation Lana Cash. "
                        + "Mots-clés attendus (encaissement) : {} {} (e-commerce) : {}",
                        MOTS_CLES_ENCAISSEMENT,
                        typeAffiliation == TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE ? "et" : "ou",
                        MOTS_CLES_ECOMMERCE
                    );
                }
                return valid;
            }
        } catch (Exception e) {
            LOGGER.warn("Validation template contrat impossible, fichier rejeté par défaut.", e);
            return false;
        }
    }

    /**
     * Retourne {@code true} si la zone signature adhérent semble remplie.
     * Appeler estFichierContratLanaCash avant cette méthode.
     * En cas d'erreur d'analyse, refuse le fichier. Une erreur technique ne doit jamais
     * débloquer automatiquement l'espace commerçant (principe fail-closed).
     *
     * Equivalent a {@code estZoneSignatureRemplie(file, 1)} : ne verifie qu'une seule
     * section signee (usage: previsualisation avant depot, sans connaitre le dossier).
     */
    public boolean estZoneSignatureRemplie(MultipartFile file) {
        return estZoneSignatureRemplie(file, 1);
    }

    /**
     * Variante consciente des dossiers combines : un PDF genere pour un dossier
     * ENCAISSEMENT_ET_ECOMMERCE (ou avec plusieurs PDV) est la fusion de plusieurs
     * sous-contrats independants, chacun avec sa propre zone de signature sur sa
     * propre derniere page. Verifier uniquement la derniere page du PDF fusionne
     * ne controle que le dernier sous-contrat et rejette a tort un contrat entierement
     * signe. Ici, on parcourt TOUTES les pages et on exige qu'au moins
     * {@code expectedSignedSections} d'entre elles montrent une zone remplie.
     *
     * @param expectedSignedSections nombre de sous-contrats attendus dans le PDF
     *                               (voir {@link ServiceDocumentContratAffiliation#resolveExpectedSignatureSections}) ;
     *                               une valeur &lt; 1 est traitee comme 1.
     */
    public boolean estZoneSignatureRemplie(MultipartFile file, int expectedSignedSections) {
        try {
            String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase()
                : "";
            byte[] bytes = file.getBytes();

            if (filename.endsWith(".pdf")) {
                return analyserPdf(bytes, expectedSignedSections);
            }
            return analyserImage(bytes);
        } catch (Exception exception) {
            LOGGER.warn("Analyse de signature impossible, le fichier est refusé par sécurité.", exception);
            return false;
        }
    }

    private boolean analyserPdf(byte[] bytes, int expectedSignedSections) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            // La simple présence d'un dictionnaire /Sig n'est pas une preuve : il peut être
            // vide, auto-signé, expiré ou ne couvrir qu'une partie du document. Tant qu'une
            // validation cryptographique complète (certificat + chaîne + horodatage + byte
            // range) n'est pas branchée, le PDF suit le même contrôle strict que les scans.
            if (!document.getSignatureDictionaries().isEmpty()) {
                LOGGER.info("Objet de signature PDF présent ; validation visuelle des sections maintenue.");
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                return false;
            }

            PDFRenderer renderer = new PDFRenderer(document);
            PDFTextStripper pageStripper = new PDFTextStripper();
            int pagesSignatureDetectees = 0;
            int pagesSignatureSignees = 0;
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                pageStripper.setStartPage(pageIndex + 1);
                pageStripper.setEndPage(pageIndex + 1);
                String pageText = normaliserTexte(pageStripper.getText(document));
                if (!pageText.contains("cachet et signature de l'adherent")) {
                    continue;
                }
                pagesSignatureDetectees++;
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
                if (analyserZone(image)) {
                    pagesSignatureSignees++;
                }
            }

            int sectionsAttendues = Math.max(expectedSignedSections, 1);
            LOGGER.info(
                "Contrat : {} page(s) de signature détectée(s), {} signée(s), {} section(s) attendue(s), {} page(s) au total.",
                pagesSignatureDetectees,
                pagesSignatureSignees,
                sectionsAttendues,
                pageCount
            );
            // Le nombre doit être exact : une page quelconque noircie ou une section
            // dupliquée ne peut pas compenser un sous-contrat absent/non signé.
            return pagesSignatureDetectees == sectionsAttendues
                && pagesSignatureSignees == sectionsAttendues;
        }
    }

    private String normaliserTexte(String value) {
        String sansAccents = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return sansAccents.toLowerCase().replace('’', '\'');
    }

    private boolean analyserImage(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            LOGGER.warn("Impossible de lire l'image uploadée.");
            return false;
        }
        return analyserZone(image);
    }

    private boolean analyserZone(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        int x0 = (int) (w * ZONE_X_START);
        int x1 = (int) (w * ZONE_X_END);
        int y0 = (int) (h * ZONE_Y_START);
        int y1 = (int) (h * ZONE_Y_END);

        long totalPixels = (long) (x1 - x0) * (y1 - y0);
        if (totalPixels == 0) {
            return false;
        }

        long pixelsFonces = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8)  & 0xFF;
                int b =  rgb        & 0xFF;
                if ((r + g + b) / 3 < BRIGHTNESS_THRESHOLD) {
                    pixelsFonces++;
                }
            }
        }

        double ratio = (double) pixelsFonces / totalPixels;
        LOGGER.info(
            "Zone signature adhérent – pixels foncés : {}/{} ({} %)",
            pixelsFonces,
            totalPixels,
            String.format("%.2f", ratio * 100)
        );

        return ratio >= DARK_PIXEL_THRESHOLD;
    }
}
