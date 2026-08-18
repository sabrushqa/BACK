package com.example.demo.services;

import com.example.demo.dto.AffiliationDocumentValidationResponse;
import com.example.demo.dto.AffiliationRibExtractionResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AffiliationDocumentValidationService {

    private static final long MAX_VALIDATOR_FILE_SIZE_BYTES = 16L * 1024L * 1024L;
    private static final Set<String> IMAGE_EXTENSIONS =
        Set.of("jpg", "jpeg", "png", "gif", "bmp");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final boolean validationEnabled;
    private final URI validationEndpoint;
    private final URI validationFallbackEndpoint;
    private final URI processingEndpoint;
    private final URI processingFallbackEndpoint;
    // Vide par défaut : doc-classifier désactive son authentification tant
    // que API_KEY n'est pas défini côté serveur (mode dev). En production,
    // définir app.affiliation.document-validator.api-key ICI ET API_KEY côté
    // doc-classifier avec la même valeur — sinon tous les appels /api/validate
    // et /api/process reçoivent 401 dès que la clé serveur est activée.
    private final String apiKey;

    public AffiliationDocumentValidationService(
        @Value("${app.affiliation.document-validator.enabled:true}") boolean validationEnabled,
        @Value("${app.affiliation.document-validator.base-url:http://127.0.0.1:9001}")
        String documentValidatorBaseUrl,
        @Value("${app.affiliation.document-validator.api-key:}") String apiKey
    ) {
        this.validationEnabled = validationEnabled;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        String normalizedBaseUrl = normalizeBaseUrl(documentValidatorBaseUrl);
        this.validationEndpoint = URI.create(normalizedBaseUrl + "/api/validate");
        this.validationFallbackEndpoint = buildFallbackEndpoint(
            normalizedBaseUrl,
            "/api/validate"
        );
        this.processingEndpoint = URI.create(normalizedBaseUrl + "/api/process");
        this.processingFallbackEndpoint = buildFallbackEndpoint(
            normalizedBaseUrl,
            "/api/process"
        );
    }

    public AffiliationDocumentValidationResponse validateDocument(
        String documentKey,
        MultipartFile file
    ) {
        ExpectedDocumentResolution expectedDocument = resolveExpectedDocument(documentKey);

        if (!expectedDocument.supported()) {
            return skippedResponse(
                documentKey,
                false,
                null,
                "Validation automatique non disponible pour ce type de document."
            );
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier du document est obligatoire.");
        }

        if (!validationEnabled) {
            return skippedResponse(
                documentKey,
                true,
                expectedDocument.expectedType(),
                "Validation automatique des documents desactivee."
            );
        }

        if (!isImageFile(file)) {
            return skippedResponse(
                documentKey,
                true,
                expectedDocument.expectedType(),
                "Validation automatique disponible uniquement pour les images (jpg, jpeg, png, gif, bmp)."
            );
        }

        if (file.getSize() > MAX_VALIDATOR_FILE_SIZE_BYTES) {
            return skippedResponse(
                documentKey,
                true,
                expectedDocument.expectedType(),
                "Validation automatique ignoree car le fichier depasse 16 MB."
            );
        }

        if ("ribDocument".equals(documentKey)) {
            return validateRibDocument(documentKey, expectedDocument, file);
        }

        ExternalDocumentValidationApiResponse apiResponse =
            callValidationApi(expectedDocument.expectedType(), file);

        boolean valid = Boolean.TRUE.equals(apiResponse.valid());
        String detectedType = normalize(apiResponse.detectedType());
        String reason = firstMeaningful(
            apiResponse.reason(),
            apiResponse.error(),
            valid
                ? "Document validé."
                : "Le document ne correspond pas au type attendu."
        );

        return new AffiliationDocumentValidationResponse(
            documentKey,
            valid ? "VALID" : "INVALID",
            true,
            true,
            expectedDocument.expectedType(),
            detectedType,
            reason,
            null
        );
    }

    public void validateUploadedDocumentsOrThrow(Map<String, MultipartFile> uploadedDocuments) {
        for (Map.Entry<String, MultipartFile> entry : uploadedDocuments.entrySet()) {
            MultipartFile file = entry.getValue();
            if (file == null || file.isEmpty()) {
                continue;
            }

            validateDocument(entry.getKey(), file);
        }
    }

    private AffiliationDocumentValidationResponse validateRibDocument(
        String documentKey,
        ExpectedDocumentResolution expectedDocument,
        MultipartFile file
    ) {
        ExternalDocumentProcessingApiResponse apiResponse = callProcessingApi(file);
        String detectedType = normalize(apiResponse.documentClass());
        boolean valid = Boolean.TRUE.equals(apiResponse.success())
            && expectedDocument.expectedType().equalsIgnoreCase(
                Objects.requireNonNullElse(detectedType, "")
            );
        AffiliationRibExtractionResponse ribExtraction = mapRibExtraction(apiResponse.extraction());
        String reason = firstMeaningful(
            apiResponse.reason(),
            apiResponse.error(),
            valid
                ? "Document RIB validé."
                : "Le type du document n'a pas pu etre confirme automatiquement."
        );

        return new AffiliationDocumentValidationResponse(
            documentKey,
            valid ? "VALID" : "INVALID",
            true,
            true,
            expectedDocument.expectedType(),
            detectedType,
            reason,
            ribExtraction
        );
    }

    private ExternalDocumentValidationApiResponse callValidationApi(
        String expectedType,
        MultipartFile file
    ) {
        try {
            return sendValidationRequest(validationEndpoint, expectedType, file);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Le service de validation documentaire ne repond pas actuellement.",
                exception
            );
        } catch (IOException primaryException) {
            if (primaryException instanceof HttpTimeoutException) {
                throw new IllegalStateException(
                    "Le service de validation documentaire met trop de temps a répondre. Réessayez dans quelques instants.",
                    primaryException
                );
            }

            if (
                validationFallbackEndpoint != null
                && !validationFallbackEndpoint.equals(validationEndpoint)
            ) {
                try {
                    return sendValidationRequest(validationFallbackEndpoint, expectedType, file);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "Le service de validation documentaire ne repond pas actuellement.",
                        exception
                    );
                } catch (IOException fallbackException) {
                    primaryException.addSuppressed(fallbackException);
                }
            }

            throw new IllegalStateException(
                "Impossible de contacter le service de validation documentaire. Vérifiez que l'API IA repond sur http://127.0.0.1:9001.",
                primaryException
            );
        }
    }

    private ExternalDocumentProcessingApiResponse callProcessingApi(MultipartFile file) {
        try {
            return sendProcessingRequest(processingEndpoint, file);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Le service de validation documentaire ne repond pas actuellement.",
                exception
            );
        } catch (IOException primaryException) {
            if (primaryException instanceof HttpTimeoutException) {
                throw new IllegalStateException(
                    "Le service de validation documentaire met trop de temps a répondre. Réessayez dans quelques instants.",
                    primaryException
                );
            }

            if (
                processingFallbackEndpoint != null
                && !processingFallbackEndpoint.equals(processingEndpoint)
            ) {
                try {
                    return sendProcessingRequest(processingFallbackEndpoint, file);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "Le service de validation documentaire ne repond pas actuellement.",
                        exception
                    );
                } catch (IOException fallbackException) {
                    primaryException.addSuppressed(fallbackException);
                }
            }

            throw new IllegalStateException(
                "Impossible de contacter le service de validation documentaire. Vérifiez que l'API IA repond sur http://127.0.0.1:9001.",
                primaryException
            );
        }
    }

    private ExternalDocumentValidationApiResponse sendValidationRequest(
        URI endpoint,
        String expectedType,
        MultipartFile file
    ) throws IOException, InterruptedException {
        String boundary = "----LanaCashDocumentValidation" + UUID.randomUUID();
        byte[] requestBody = buildMultipartRequestBody(
            boundary,
            Map.of("expected_type", expectedType),
            file
        );

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(90))
            .header(
                "Content-Type",
                MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary
            );
        if (StringUtils.hasText(apiKey)) {
            builder.header("X-API-Key", apiKey);
        }
        HttpRequest request = builder
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        ExternalDocumentValidationApiResponse payload = parseApiResponse(response.body());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return payload;
        }

        String errorMessage = firstMeaningful(
            payload.error(),
            payload.reason(),
            "Le service de validation documentaire a refusé le fichier."
        );

        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            throw new IllegalArgumentException(errorMessage);
        }

        throw new IllegalStateException(errorMessage);
    }

    private ExternalDocumentProcessingApiResponse sendProcessingRequest(
        URI endpoint,
        MultipartFile file
    ) throws IOException, InterruptedException {
        String boundary = "----LanaCashDocumentValidation" + UUID.randomUUID();
        byte[] requestBody = buildMultipartRequestBody(boundary, Map.of(), file);

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(90))
            .header(
                "Content-Type",
                MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary
            );
        if (StringUtils.hasText(apiKey)) {
            builder.header("X-API-Key", apiKey);
        }
        HttpRequest request = builder
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        ExternalDocumentProcessingApiResponse payload = parseProcessingApiResponse(response.body());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return payload;
        }

        String errorMessage = firstMeaningful(
            payload.error(),
            payload.reason(),
            "Le service d'extraction documentaire a refusé le fichier."
        );

        if (response.statusCode() >= 400 && response.statusCode() < 500) {
            throw new IllegalArgumentException(errorMessage);
        }

        throw new IllegalStateException(errorMessage);
    }

    private byte[] buildMultipartRequestBody(
        String boundary,
        Map<String, String> textFields,
        MultipartFile file
    ) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (Map.Entry<String, String> entry : new LinkedHashMap<>(textFields).entrySet()) {
                writeTextPart(outputStream, boundary, entry.getKey(), entry.getValue());
            }
            writeFilePart(outputStream, boundary, "file", file);
            outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Impossible de preparer le document pour la validation.",
                exception
            );
        }
    }

    private void writeTextPart(
        ByteArrayOutputStream outputStream,
        String boundary,
        String fieldName,
        String value
    ) throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(
            (
                "Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n"
                    + Objects.requireNonNullElse(value, "")
                    + "\r\n"
            ).getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeFilePart(
        ByteArrayOutputStream outputStream,
        String boundary,
        String fieldName,
        MultipartFile file
    ) throws IOException {
        String fileName = StringUtils.hasText(file.getOriginalFilename())
            ? StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()))
            : "document";
        String contentType = StringUtils.hasText(file.getContentType())
            ? Objects.requireNonNull(file.getContentType())
            : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(
            (
                "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8)
        );
        outputStream.write(file.getBytes());
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private ExternalDocumentValidationApiResponse parseApiResponse(String rawBody)
        throws IOException {
        if (!StringUtils.hasText(rawBody)) {
            return new ExternalDocumentValidationApiResponse(null, null, null, null, null);
        }

        return objectMapper.readValue(rawBody, ExternalDocumentValidationApiResponse.class);
    }

    private ExternalDocumentProcessingApiResponse parseProcessingApiResponse(String rawBody)
        throws IOException {
        if (!StringUtils.hasText(rawBody)) {
            return new ExternalDocumentProcessingApiResponse(
                null,
                null,
                null,
                null,
                null
            );
        }

        return objectMapper.readValue(rawBody, ExternalDocumentProcessingApiResponse.class);
    }

    private boolean isImageFile(MultipartFile file) {
        String originalFilename = normalize(file.getOriginalFilename());
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return false;
        }

        String extension = originalFilename
            .substring(originalFilename.lastIndexOf('.') + 1)
            .toLowerCase(Locale.ROOT);

        return IMAGE_EXTENSIONS.contains(extension);
    }

    private ExpectedDocumentResolution resolveExpectedDocument(String documentKey) {
        return switch (Objects.requireNonNullElse(normalize(documentKey), "")) {
            case "cinDocument" -> new ExpectedDocumentResolution(true, "CIN", "CIN");
            case "ribDocument" -> new ExpectedDocumentResolution(true, "RIB", "RIB");
            case "patenteDocument" -> new ExpectedDocumentResolution(true, "PATENTE", "Patente");
            case "statutsDocument" -> new ExpectedDocumentResolution(true, "STATUS", "Statuts");
            case "rcDocument" -> new ExpectedDocumentResolution(true, "RC", "RC");
            case "iceDocument" -> new ExpectedDocumentResolution(true, "ICE", "ICE");
            case "cinRepresentantDocument" ->
                new ExpectedDocumentResolution(true, "CIN", "CIN representant legal");
            case "attestationAeDocument" ->
                new ExpectedDocumentResolution(true, "CARTE_AE", "Attestation AE");
            case "cinSignataireDocument" ->
                new ExpectedDocumentResolution(true, "CIN", "CIN signataire");
            case "pvNominationDocument" ->
                new ExpectedDocumentResolution(false, null, "PV de nomination");
            case "pvAssociationDocument" ->
                new ExpectedDocumentResolution(false, null, "PV assemblee generale");
            case "listeMembresDocument" ->
                new ExpectedDocumentResolution(false, null, "Liste des membres");
            default -> throw new IllegalArgumentException("Type de document inconnu.");
        };
    }

    private AffiliationDocumentValidationResponse skippedResponse(
        String documentKey,
        boolean supported,
        String expectedType,
        String reason
    ) {
        return new AffiliationDocumentValidationResponse(
            documentKey,
            "SKIPPED",
            supported,
            false,
            expectedType,
            null,
            reason,
            null
        );
    }

    private AffiliationRibExtractionResponse mapRibExtraction(
        ExternalRibExtractionApiResponse extraction
    ) {
        if (extraction == null) {
            return null;
        }

        String codeBanque = compactAlphaNumeric(extraction.codeBanque());
        String codeVille = compactAlphaNumeric(extraction.codeVille());
        String explicitRib = firstMeaningful(
            compactAlphaNumeric(extraction.rib()),
            compactAlphaNumeric(extraction.numeroRib())
        );
        String numeroCompte = compactAlphaNumeric(extraction.numeroCompte());
        String cleRib = compactAlphaNumeric(extraction.cleRib());
        String devise = compactAlphaNumeric(extraction.devise());
        String iban = compactAlphaNumeric(extraction.iban());
        String swift = compactAlphaNumeric(extraction.swift());
        String ribValue = firstMeaningful(
            explicitRib,
            buildRibValue(
            codeBanque,
            codeVille,
            numeroCompte,
            cleRib,
            devise
            )
        );

        if (
            !StringUtils.hasText(ribValue)
            && !StringUtils.hasText(extraction.banque())
            && !StringUtils.hasText(extraction.titulaire())
            && !StringUtils.hasText(iban)
            && !StringUtils.hasText(swift)
        ) {
            return null;
        }

        return new AffiliationRibExtractionResponse(
            ribValue,
            normalize(extraction.banque()),
            normalize(extraction.titulaire()),
            codeBanque,
            codeVille,
            numeroCompte,
            cleRib,
            devise,
            iban,
            swift
        );
    }

    private String buildRibValue(
        String codeBanque,
        String codeVille,
        String numeroCompte,
        String cleRib,
        String devise
    ) {
        if (!isReliableRibSegments(codeBanque, codeVille, numeroCompte, cleRib)) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        appendRibSegment(builder, codeBanque);
        appendRibSegment(builder, codeVille);
        appendRibSegment(builder, numeroCompte);
        appendRibSegment(builder, cleRib);
        appendRibSegment(builder, devise);
        return builder.toString().trim();
    }

    private boolean isReliableRibSegments(
        String codeBanque,
        String codeVille,
        String numeroCompte,
        String cleRib
    ) {
        return StringUtils.hasText(codeBanque)
            && StringUtils.hasText(codeVille)
            && StringUtils.hasText(numeroCompte)
            && StringUtils.hasText(cleRib);
    }

    private void appendRibSegment(StringBuilder builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(' ');
        }

        builder.append(value);
    }

    private String compactAlphaNumeric(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String compactValue = value
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT)
            .trim();

        return StringUtils.hasText(compactValue) ? compactValue : null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String firstMeaningful(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalizedBaseUrl = Objects.requireNonNullElse(baseUrl, "").trim();
        if (!StringUtils.hasText(normalizedBaseUrl)) {
            return "http://127.0.0.1:9001";
        }
        return normalizedBaseUrl.endsWith("/")
            ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1)
            : normalizedBaseUrl;
    }

    private URI buildFallbackEndpoint(String normalizedBaseUrl, String endpointPath) {
        if (!normalizedBaseUrl.contains("://localhost")) {
            return null;
        }

        String fallbackBaseUrl = normalizedBaseUrl.replace("://localhost", "://127.0.0.1");
        return URI.create(fallbackBaseUrl + endpointPath);
    }

    private record ExpectedDocumentResolution(
        boolean supported,
        String expectedType,
        String label
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalDocumentValidationApiResponse(
        @JsonProperty("valid") Boolean valid,
        @JsonProperty("expected_type") String expectedType,
        @JsonProperty("detected_type") String detectedType,
        @JsonProperty("reason") String reason,
        @JsonProperty("error") String error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalDocumentProcessingApiResponse(
        @JsonProperty("success") Boolean success,
        @JsonProperty("document_class") String documentClass,
        @JsonProperty("reason") String reason,
        @JsonProperty("error") String error,
        @JsonProperty("extraction") ExternalRibExtractionApiResponse extraction
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExternalRibExtractionApiResponse(
        @JsonProperty("titulaire") String titulaire,
        @JsonProperty("banque") String banque,
        @JsonProperty("rib") String rib,
        @JsonProperty("numero_rib") String numeroRib,
        @JsonProperty("code_banque") String codeBanque,
        @JsonProperty("code_ville") String codeVille,
        @JsonProperty("numero_compte") String numeroCompte,
        @JsonProperty("cle_rib") String cleRib,
        @JsonProperty("devise") String devise,
        @JsonProperty("iban") String iban,
        @JsonProperty("swift") String swift
    ) {
    }
}
