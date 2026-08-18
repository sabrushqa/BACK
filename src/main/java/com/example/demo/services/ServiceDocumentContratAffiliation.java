package com.example.demo.services;

import com.example.demo.entities.documents;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.PdvRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServiceDocumentContratAffiliation {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceDocumentContratAffiliation.class);
    private static final List<GenerateurModeleContratAffiliation.CommercialReportLayoutMode> COMMERCIAL_REPORT_LAYOUTS =
        List.of(
            GenerateurModeleContratAffiliation.CommercialReportLayoutMode.STANDARD,
            GenerateurModeleContratAffiliation.CommercialReportLayoutMode.COMPACT,
            GenerateurModeleContratAffiliation.CommercialReportLayoutMode.ULTRA_COMPACT,
            GenerateurModeleContratAffiliation.CommercialReportLayoutMode.ONE_PAGE
        );

    private final Path contractRoot;
    private final GenerateurModeleContratAffiliation contractTemplateRenderer;
    private final PdvRepository pdvRepository;
    private final boolean chromePdfEnabled;
    private final String configuredChromeExecutable;

    public ServiceDocumentContratAffiliation(
        GenerateurModeleContratAffiliation contractTemplateRenderer,
        PdvRepository pdvRepository,
        @Value("${app.affiliation.contract-dir:uploads/contracts}") String contractDirectory,
        @Value("${app.affiliation.chrome-pdf.enabled:true}") boolean chromePdfEnabled,
        @Value("${app.affiliation.chrome-pdf.executable:}") String configuredChromeExecutable
    ) {
        this.contractTemplateRenderer = contractTemplateRenderer;
        this.pdvRepository = pdvRepository;
        this.contractRoot = Path.of(contractDirectory).toAbsolutePath().normalize();
        this.chromePdfEnabled = chromePdfEnabled;
        this.configuredChromeExecutable = configuredChromeExecutable;
    }

    public ContratGenere genererContrat(dossier_affiliation dossier) {
        try {
            Path dossierDirectory = contractRoot.resolve("dossier-" + dossier.getIdDossier());
            Files.createDirectories(dossierDirectory);

            List<pdv> pointsVente = resolvePointsVente(dossier);
            boolean hasMultipleContracts = pointsVente.size() > 1
                || dossier.getTypeAffiliation() == TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE;
            String fileName = hasMultipleContracts
                ? "contrats-affiliation-" + dossier.getIdDossier() + ".pdf"
                : "contrat-affiliation-" + dossier.getIdDossier() + ".pdf";
            Path destination = dossierDirectory.resolve(fileName);
            Files.write(destination, renderContratsPdf(dossier, pointsVente));

            return new ContratGenere(fileName, destination.toString(), LocalDate.now());
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de générer le contrat du dossier."
            );
        }
    }

    /**
     * Nombre de sous-contrats attendus dans le PDF genere pour ce dossier (voir
     * {@link #renderContratsPdf}) : un contrat par PDV (au moins 1), plus un
     * contrat e-commerce supplementaire pour les dossiers combines. Utilise par
     * {@link ContratSignatureDetector#estZoneSignatureRemplie(MultipartFile, int)}
     * pour savoir combien de zones de signature (une par sous-contrat, chacune sur
     * sa propre derniere page) doivent etre remplies dans le contrat re-depose.
     */
    public int resolveExpectedSignatureSections(dossier_affiliation dossier) {
        int base = Math.max(resolvePointsVente(dossier).size(), 1);
        return dossier.getTypeAffiliation() == TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE ? base + 1 : base;
    }

    private List<pdv> resolvePointsVente(dossier_affiliation dossier) {
        if (dossier == null || dossier.getCommercant() == null || dossier.getCommercant().getIdCommercant() == null) {
            return List.of();
        }
        if ("NOUVEAU_PDV".equalsIgnoreCase(nullToEmpty(dossier.getOrigineCreation()))
            && dossier.getRequestedPdv() != null) {
            return List.of(dossier.getRequestedPdv());
        }

        List<pdv> pointsVente = pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(
            dossier.getCommercant().getIdCommercant()
        );
        return pointsVente == null ? List.of() : pointsVente;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private byte[] renderContratsPdf(dossier_affiliation dossier, List<pdv> pointsVente) {
        if (dossier.getTypeAffiliation() == TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE) {
            // Dossier combine : un contrat encaissement par PDV (comme pour un dossier
            // encaissement seul) + un contrat e-commerce, le tout fusionne en un seul PDF.
            List<byte[]> documents = new ArrayList<>();
            if (pointsVente == null || pointsVente.isEmpty()) {
                documents.add(renderPdf(contractTemplateRenderer.genererHtmlContrat(dossier)));
            } else {
                for (pdv pointVente : pointsVente) {
                    documents.add(renderPdf(contractTemplateRenderer.genererHtmlContrat(dossier, pointVente)));
                }
            }
            documents.add(renderPdf(contractTemplateRenderer.genererHtmlContratEcommerceForce(dossier)));
            return mergePdfDocuments(documents);
        }

        if (pointsVente == null || pointsVente.isEmpty()) {
            return renderPdf(contractTemplateRenderer.genererHtmlContrat(dossier));
        }

        if (pointsVente.size() == 1) {
            return renderPdf(contractTemplateRenderer.genererHtmlContrat(dossier, pointsVente.get(0)));
        }

        return mergePdfDocuments(
            pointsVente
                .stream()
                .map(pointVente -> renderPdf(contractTemplateRenderer.genererHtmlContrat(dossier, pointVente)))
                .toList()
        );
    }

    public CompteRenduCommercialGenere genererCompteRenduCommercial(
        dossier_affiliation dossier
    ) {
        try {
            Path dossierDirectory = contractRoot.resolve("dossier-" + dossier.getIdDossier());
            Files.createDirectories(dossierDirectory);

            String fileName = "compte-rendu-commercial-" + dossier.getIdDossier() + ".pdf";
            Path destination = dossierDirectory.resolve(fileName);
            Files.write(destination, renderCommercialReportPdf(dossier));

            return new CompteRenduCommercialGenere(
                fileName,
                destination.toString(),
                LocalDate.now()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de générer le compte-rendu commercial du dossier."
            );
        }
    }

    public byte[] genererDossierComplet(
        dossier_affiliation dossier,
        List<documents> documentsDeposes,
        byte[] contratPdf,
        byte[] compteRenduPdf,
        List<DocumentAFusionner> documentsAFusionner
    ) {
        return genererDossierComplet(dossier, documentsDeposes, contratPdf, compteRenduPdf, documentsAFusionner, List.of());
    }

    /**
     * extensionSections : contrat(s) (et compte-rendu le cas echeant) de
     * chaque demande d'extension (NOUVEAU_PDV) approuvee de ce commerçant,
     * ajoutes a la suite du dossier principal — sans ca, le "dossier complet"
     * telecharge ne reflete jamais qu'un commerçant a etendu son affiliation
     * (nouveau PDV/TPE/canal e-commerce) apres l'affiliation initiale.
     */
    public byte[] genererDossierComplet(
        dossier_affiliation dossier,
        List<documents> documentsDeposes,
        byte[] contratPdf,
        byte[] compteRenduPdf,
        List<DocumentAFusionner> documentsAFusionner,
        List<byte[]> extensionSections
    ) {
        List<byte[]> sections = new ArrayList<>();
        sections.add(
            renderPdf(contractTemplateRenderer.genererHtmlFicheAutoAffiliation(dossier, documentsDeposes))
        );

        if (contratPdf != null && contratPdf.length > 0) {
            sections.add(contratPdf);
        }
        if (compteRenduPdf != null && compteRenduPdf.length > 0) {
            sections.add(compteRenduPdf);
        }
        if (documentsAFusionner != null) {
            for (DocumentAFusionner document : documentsAFusionner) {
                byte[] page = convertToMergeablePdf(document);
                if (page != null) {
                    sections.add(page);
                }
            }
        }
        if (extensionSections != null) {
            for (byte[] section : extensionSections) {
                if (section != null && section.length > 0) {
                    sections.add(section);
                }
            }
        }

        return mergePdfDocuments(sections);
    }

    private byte[] convertToMergeablePdf(DocumentAFusionner document) {
        String contentType = document.contentType() == null
            ? ""
            : document.contentType().toLowerCase(Locale.ROOT);

        if (contentType.contains("pdf")) {
            return document.content();
        }

        if (contentType.contains("jpeg") || contentType.contains("jpg")
            || contentType.contains("png") || contentType.contains("gif")
            || contentType.contains("tif")) {
            return renderImageAsPdfPage(document.content());
        }

        LOGGER.warn(
            "Document {} ({}) non fusionnable dans le PDF complet du dossier — format non pris en charge.",
            document.fileName(),
            contentType
        );
        return null;
    }

    private byte[] renderImageAsPdfPage(byte[] imageBytes) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "document");
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margin = 20f;
            float maxWidth = page.getMediaBox().getWidth() - margin * 2;
            float maxHeight = page.getMediaBox().getHeight() - margin * 2;
            float scale = Math.min(
                Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight()),
                1f
            );
            float drawWidth = image.getWidth() * scale;
            float drawHeight = image.getHeight() * scale;
            float x = (page.getMediaBox().getWidth() - drawWidth) / 2;
            float y = (page.getMediaBox().getHeight() - drawHeight) / 2;

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(image, x, y, drawWidth, drawHeight);
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            LOGGER.warn("Impossible de convertir un document image en page PDF pour la fusion.", exception);
            return null;
        }
    }

    public ContratSigneEnregistre enregistrerContratSigne(
        dossier_affiliation dossier,
        MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le contrat signé est obligatoire.");
        }

        try {
            Path signedDirectory = contractRoot
                .resolve("dossier-" + dossier.getIdDossier())
                .resolve("signed");
            Files.createDirectories(signedDirectory);

            String originalName = sanitizeFileName(file.getOriginalFilename());
            String storedName =
                "contrat-signé-" + System.currentTimeMillis() + "-" + originalName;
            Path destination = signedDirectory.resolve(storedName);
            file.transferTo(destination.toFile());

            return new ContratSigneEnregistre(
                originalName,
                destination.toString(),
                LocalDate.now()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible d'enregistrer le contrat signé."
            );
        }
    }

    public ContratTelecharge telechargerFichier(String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Le contrat demande est introuvable."
            );
        }

        Path path = resolveExistingContractPath(storedPath);
        if (!isExistingFile(path)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Le contrat demande est introuvable."
            );
        }

        try {
            String contentType = resolveContentType(path);
            return new ContratTelecharge(
                resolveFileName(storedPath),
                StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                Files.readAllBytes(path)
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de charger le contrat."
            );
        }
    }

    public boolean fichierDisponible(String storedPath) {
        return isExistingFile(resolveExistingContractPath(storedPath));
    }

    public String resolveFileName(String rawPath) {
        String normalizedValue = Objects.requireNonNullElse(rawPath, "").trim().replace('\\', '/');
        if (!StringUtils.hasText(normalizedValue)) {
            return "contrat";
        }

        int slashIndex = normalizedValue.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedValue.substring(slashIndex + 1) : normalizedValue;
        return StringUtils.hasText(fileName) ? fileName : "contrat";
    }

    private Path resolveExistingContractPath(String storedPath) {
        Path directPath = normalizePath(storedPath);
        if (isExistingFile(directPath)) {
            return directPath;
        }

        Path relocatedPath = resolvePathWithinRoot(storedPath, contractRoot);
        return isExistingFile(relocatedPath) ? relocatedPath : null;
    }

    private Path resolvePathWithinRoot(String rawPath, Path root) {
        String normalizedValue = Objects.requireNonNullElse(rawPath, "").trim().replace('\\', '/');
        if (!StringUtils.hasText(normalizedValue)) {
            return null;
        }

        String[] segments = normalizedValue.split("/");
        int relativeStartIndex = -1;
        for (int index = 0; index < segments.length; index++) {
            if (segments[index].startsWith("dossier-")) {
                relativeStartIndex = index;
                break;
            }
        }

        if (relativeStartIndex >= 0) {
            Path candidate = root;
            for (int index = relativeStartIndex; index < segments.length; index++) {
                if (StringUtils.hasText(segments[index])) {
                    candidate = candidate.resolve(segments[index]);
                }
            }
            return candidate.normalize();
        }

        String fileName = resolveFileName(normalizedValue);
        return StringUtils.hasText(fileName) ? root.resolve(fileName).normalize() : null;
    }

    private Path normalizePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }

        try {
            return Path.of(rawPath).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isExistingFile(Path path) {
        return path != null && Files.exists(path) && !Files.isDirectory(path);
    }

    private String sanitizeFileName(String originalFilename) {
        String cleanFileName = StringUtils.cleanPath(
            Objects.requireNonNullElse(originalFilename, "contrat-signé")
        );
        String sanitized = cleanFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "contrat-signé" : sanitized;
    }

    private byte[] renderPdf(String htmlContent) {
        if (chromePdfEnabled) {
            try {
                LOGGER.info("Generation du contrat PDF via Chromium headless.");
                return renderPdfWithChrome(htmlContent);
            } catch (InterruptedException exception) {
                // Sonar S2142 : ne jamais avaler InterruptedException sans re-interrompre
                // le thread, sinon on efface un signal d'interruption demande par la JVM
                // (ex: arret propre du serveur) — le fallback OpenHTMLToPDF reste
                // identique, seul le statut d'interruption du thread est desormais propage.
                Thread.currentThread().interrupt();
                LOGGER.warn(
                    "Rendu Chromium du contrat interrompu. Bascule vers OpenHTMLToPDF.",
                    exception
                );
            } catch (Exception exception) {
                LOGGER.warn(
                    "Le rendu Chromium du contrat a echoue. Bascule vers OpenHTMLToPDF.",
                    exception
                );
            }
        }

        LOGGER.info("Generation du contrat PDF via OpenHTMLToPDF.");
        return renderPdfWithOpenHtmlToPdf(htmlContent);
    }

    private byte[] renderCommercialReportPdf(dossier_affiliation dossier) {
        byte[] selectedPdf = new byte[0];
        int selectedPageCount = Integer.MAX_VALUE;
        GenerateurModeleContratAffiliation.CommercialReportLayoutMode selectedLayout =
            GenerateurModeleContratAffiliation.CommercialReportLayoutMode.STANDARD;

        for (GenerateurModeleContratAffiliation.CommercialReportLayoutMode layoutMode : COMMERCIAL_REPORT_LAYOUTS) {
            byte[] candidatePdf = renderPdfWithOpenHtmlToPdf(
                contractTemplateRenderer.genererHtmlCompteRenduCommercial(dossier, layoutMode)
            );
            int candidatePageCount = countPdfPages(candidatePdf);

            selectedPdf = candidatePdf;
            selectedPageCount = candidatePageCount;
            selectedLayout = layoutMode;

            if (candidatePageCount <= 1) {
                break;
            }
        }

        if (selectedPageCount > 1) {
            selectedPdf = keepFirstPdfPage(selectedPdf);
            LOGGER.warn(
                "Le compte-rendu commercial du dossier {} a été force sur une page A4 après compactage ({} pages initiales, layout={}).",
                dossier == null ? null : dossier.getIdDossier(),
                selectedPageCount,
                selectedLayout
            );
        } else {
            LOGGER.info(
                "Compte-rendu commercial du dossier {} généré sur {} page A4 (layout={}).",
                dossier == null ? null : dossier.getIdDossier(),
                selectedPageCount,
                selectedLayout
            );
        }

        return selectedPdf;
    }

    private byte[] keepFirstPdfPage(byte[] pdfContent) {
        try (
            PDDocument sourceDocument = PDDocument.load(pdfContent);
            PDDocument singlePageDocument = new PDDocument();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            if (sourceDocument.getNumberOfPages() == 0) {
                return pdfContent;
            }

            singlePageDocument.importPage(sourceDocument.getPage(0));
            singlePageDocument.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de verrouiller le compte-rendu commercial sur une page A4."
            );
        }
    }

    private int countPdfPages(byte[] pdfContent) {
        try (PDDocument document = PDDocument.load(pdfContent)) {
            return document.getNumberOfPages();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de verifier le format du compte-rendu commercial."
            );
        }
    }

    private byte[] renderPdfWithOpenHtmlToPdf(String htmlContent) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlContent, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de générer le contrat du dossier."
            );
        }
    }

    private byte[] mergePdfDocuments(List<byte[]> pdfDocuments) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(outputStream);

            for (byte[] pdfDocument : pdfDocuments) {
                merger.addSource(new ByteArrayInputStream(pdfDocument));
            }

            merger.mergeDocuments(null);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de générer les contrats du dossier."
            );
        }
    }

    /**
     * Cree un repertoire temporaire accessible au seul proprietaire du processus
     * (permissions POSIX rwx------) au lieu du repertoire temp partage par defaut
     * (Sonar S5443) : ce dossier contient le HTML/PDF d'un contrat en cours de
     * generation (donnees personnelles du commercant) et ne doit pas etre
     * lisible par d'autres utilisateurs locaux de la machine. Sur un OS sans
     * permissions POSIX (Windows), on retombe sur le comportement par defaut.
     */
    // Visibilite package (au lieu de private) pour etre testable directement
    // sans reflexion depuis le meme package (voir ServiceDocumentContratAffiliationTempDirectoryTest).
    Path createRestrictedTempDirectory(String prefix) throws IOException {
        try {
            return Files.createTempDirectory(
                prefix,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
            );
        } catch (UnsupportedOperationException posixUnsupported) {
            // Repli Windows (pas de permissions POSIX) : le systeme de fichiers
            // n'a pas la meme notion de repertoire "publiquement inscriptible"
            // qu'un /tmp Unix partage — le dossier temp y est deja scope par
            // profil utilisateur (%LOCALAPPDATA%/Temp). Faux positif Sonar S5443
            // sur ce repli precis, la branche POSIX ci-dessus reste la protection
            // reelle sur les OS concernes par la regle.
            return Files.createTempDirectory(prefix); // NOSONAR java:S5443
        }
    }

    private byte[] renderPdfWithChrome(String htmlContent) throws IOException, InterruptedException {
        String chromeExecutable = resolveChromeExecutable();
        if (!StringUtils.hasText(chromeExecutable)) {
            throw new IOException("Navigateur Chromium introuvable.");
        }

        Path tempDirectory = createRestrictedTempDirectory("lana-contract-render-");
        Path htmlFile = tempDirectory.resolve("contract.html");
        Path pdfFile = tempDirectory.resolve("contract.pdf");

        try {
            Files.writeString(htmlFile, htmlContent, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(chromeExecutable);
            command.add("--headless=new");
            command.add("--disable-gpu");
            command.add("--run-all-compositor-stages-before-draw");
            command.add("--virtual-time-budget=1500");
            command.add("--print-to-pdf-no-header");
            command.add("--no-pdf-header-footer");
            command.add("--print-to-pdf=" + pdfFile.toAbsolutePath());
            command.add(htmlFile.toUri().toString());

            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode != 0 || !Files.exists(pdfFile)) {
                throw new IOException(
                    StringUtils.hasText(processOutput)
                        ? processOutput
                        : "Le rendu Chromium du contrat a echoue."
                );
            }

            return Files.readAllBytes(pdfFile);
        } finally {
            try {
                Files.deleteIfExists(pdfFile);
            } catch (IOException ignored) {
            }
            try {
                Files.deleteIfExists(htmlFile);
            } catch (IOException ignored) {
            }
            try {
                Files.deleteIfExists(tempDirectory);
            } catch (IOException ignored) {
            }
        }
    }

    private String resolveChromeExecutable() {
        if (StringUtils.hasText(configuredChromeExecutable)) {
            Path configuredPath = Path.of(configuredChromeExecutable).toAbsolutePath().normalize();
            if (Files.exists(configuredPath)) {
                return configuredPath.toString();
            }
        }

        String[] candidates = new String[] {
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary",
            "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable",
            "/usr/bin/chromium-browser",
            "/usr/bin/chromium",
            "/snap/bin/chromium"
        };

        for (String candidate : candidates) {
            Path candidatePath = Path.of(candidate);
            if (Files.exists(candidatePath)) {
                return candidatePath.toAbsolutePath().normalize().toString();
            }
        }

        String[] pathCommands = new String[] {
            "google-chrome",
            "google-chrome-stable",
            "chromium-browser",
            "chromium",
            "msedge"
        };

        for (String pathCommand : pathCommands) {
            String resolvedPath = findExecutableOnPath(pathCommand);
            if (StringUtils.hasText(resolvedPath)) {
                return resolvedPath;
            }
        }

        return "";
    }

    private String findExecutableOnPath(String executableName) {
        String pathEnv = System.getenv("PATH");
        if (!StringUtils.hasText(pathEnv) || !StringUtils.hasText(executableName)) {
            return "";
        }

        String[] pathEntries = pathEnv.split(File.pathSeparator);
        for (String pathEntry : pathEntries) {
            if (!StringUtils.hasText(pathEntry)) {
                continue;
            }

            try {
                Path candidate = Path.of(pathEntry, executableName);
                if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath().normalize().toString();
                }
            } catch (Exception ignored) {
                // Ignore malformed PATH entries and continue scanning.
            }
        }

        return "";
    }

    private String resolveContentType(Path path) throws IOException {
        String contentType = Files.probeContentType(path);
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }

        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        }

        return "application/octet-stream";
    }

    public record ContratGenere(String nomFichier, String cheminStocke, LocalDate dateGeneration) {
    }

    public record CompteRenduCommercialGenere(
        String nomFichier,
        String cheminStocke,
        LocalDate dateGeneration
    ) {
    }

    public record ContratSigneEnregistre(
        String nomFichierOriginal,
        String cheminStocke,
        LocalDate dateDepot
    ) {
    }

    public record ContratTelecharge(String nomFichier, String typeContenu, byte[] contenu) {
        // Voir StaffAffiliationManagementService.DocumentDownload : equals/hashCode
        // par defaut sur un champ tableau compare des references, pas le contenu
        // (Sonar S6218).
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ContratTelecharge that)) {
                return false;
            }
            return Objects.equals(nomFichier, that.nomFichier)
                && Objects.equals(typeContenu, that.typeContenu)
                && Arrays.equals(contenu, that.contenu);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nomFichier, typeContenu, Arrays.hashCode(contenu));
        }

        @Override
        public String toString() {
            return "ContratTelecharge[nomFichier=" + nomFichier
                + ", typeContenu=" + typeContenu
                + ", contenu=" + (contenu == null ? "null" : contenu.length + " octets") + "]";
        }
    }

    public record DocumentAFusionner(String fileName, String contentType, byte[] content) {
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DocumentAFusionner that)) {
                return false;
            }
            return Objects.equals(fileName, that.fileName)
                && Objects.equals(contentType, that.contentType)
                && Arrays.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileName, contentType, Arrays.hashCode(content));
        }

        @Override
        public String toString() {
            return "DocumentAFusionner[fileName=" + fileName
                + ", contentType=" + contentType
                + ", content=" + (content == null ? "null" : content.length + " octets") + "]";
        }
    }
}
