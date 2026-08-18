package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.AffiliationDocumentValidationResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Tests unitaires purs: quand la validation automatique est desactivee
 * (app.affiliation.document-validator.enabled=false), le service ne doit
 * jamais appeler le microservice externe et retourner une reponse "skipped".
 * D'autres tests demarrent un faux serveur HTTP local pour exercer les
 * appels reels vers l'API externe de validation/extraction documentaire.
 */
class AffiliationDocumentValidationServiceTest {

    private final AffiliationDocumentValidationService serviceDisabled =
        new AffiliationDocumentValidationService(false, "http://127.0.0.1:9001", "");

    private HttpServer fakeApiServer;

    @AfterEach
    void stopFakeApiServer() {
        if (fakeApiServer != null) {
            fakeApiServer.stop(0);
        }
    }

    private String startFakeApi(String path, int statusCode, String body) throws IOException {
        fakeApiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeApiServer.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        fakeApiServer.start();
        return "http://127.0.0.1:" + fakeApiServer.getAddress().getPort();
    }

    @Test
    void skipsValidationWhenDisabled() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "cin.jpg", "image/jpeg", "fake-image-bytes".getBytes()
        );

        AffiliationDocumentValidationResponse response = serviceDisabled.validateDocument("cinDocument", file);

        assertThat(response.performed()).isFalse();
        assertThat(response.reason()).contains("desactivee");
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> serviceDisabled.validateDocument("cinDocument", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "cin.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> serviceDisabled.validateDocument("cinDocument", file))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownDocumentKey() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "doc.jpg", "image/jpeg", "fake-image-bytes".getBytes()
        );

        assertThatThrownBy(() -> serviceDisabled.validateDocument("documentKeyInexistant", file))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skipsValidationForUnsupportedDocumentType() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "doc.png", "image/png", new byte[] {1, 2, 3}
        );

        AffiliationDocumentValidationResponse response =
            serviceDisabled.validateDocument("pvNominationDocument", file);

        assertThat(response.status()).isEqualTo("SKIPPED");
        assertThat(response.supported()).isFalse();
    }

    @Test
    void skipsWhenFileIsNotAnImage() {
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, "http://127.0.0.1:9001", "");
        MockMultipartFile file = new MockMultipartFile(
            "file", "doc.pdf", "application/pdf", new byte[] {1}
        );

        AffiliationDocumentValidationResponse response = validationService.validateDocument("cinDocument", file);

        assertThat(response.status()).isEqualTo("SKIPPED");
        assertThat(response.reason()).contains("images");
    }

    @Test
    void skipsWhenFileExceedsMaxSize() {
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, "http://127.0.0.1:9001", "");
        byte[] oversized = new byte[17 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "doc.png", "image/png", oversized);

        AffiliationDocumentValidationResponse response = validationService.validateDocument("cinDocument", file);

        assertThat(response.status()).isEqualTo("SKIPPED");
        assertThat(response.reason()).contains("16 MB");
    }

    @Test
    void validatesDocumentSuccessfullyAgainstExternalApi() throws IOException {
        String baseUrl = startFakeApi(
            "/api/validate",
            200,
            "{\"valid\":true,\"detected_type\":\"CIN\",\"reason\":\"ok\"}"
        );
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        AffiliationDocumentValidationResponse response = validationService.validateDocument(
            "cinDocument",
            new MockMultipartFile("file", "cin.png", "image/png", new byte[] {1, 2, 3})
        );

        assertThat(response.status()).isEqualTo("VALID");
        assertThat(response.detectedType()).isEqualTo("CIN");
        assertThat(response.performed()).isTrue();
    }

    @Test
    void marksDocumentInvalidWhenExternalApiRejectsIt() throws IOException {
        String baseUrl = startFakeApi(
            "/api/validate",
            200,
            "{\"valid\":false,\"detected_type\":\"RIB\",\"reason\":\"mismatch\"}"
        );
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        AffiliationDocumentValidationResponse response = validationService.validateDocument(
            "cinDocument",
            new MockMultipartFile("file", "cin.png", "image/png", new byte[] {1, 2, 3})
        );

        assertThat(response.status()).isEqualTo("INVALID");
        assertThat(response.reason()).isEqualTo("mismatch");
    }

    @Test
    void throwsIllegalArgumentWhenApiReturnsClientError() throws IOException {
        String baseUrl = startFakeApi("/api/validate", 400, "{\"error\":\"fichier illisible\"}");
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        assertThatThrownBy(() ->
            validationService.validateDocument(
                "cinDocument",
                new MockMultipartFile("file", "cin.png", "image/png", new byte[] {1, 2, 3})
            )
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fichier illisible");
    }

    @Test
    void throwsIllegalStateWhenApiReturnsServerError() throws IOException {
        String baseUrl = startFakeApi("/api/validate", 500, "{\"error\":\"crash interne\"}");
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        assertThatThrownBy(() ->
            validationService.validateDocument(
                "cinDocument",
                new MockMultipartFile("file", "cin.png", "image/png", new byte[] {1, 2, 3})
            )
        ).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("crash interne");
    }

    @Test
    void validatesRibDocumentAndExtractsFields() throws IOException {
        String baseUrl = startFakeApi(
            "/api/process",
            200,
            "{\"success\":true,\"document_class\":\"RIB\",\"reason\":\"ok\","
                + "\"extraction\":{\"titulaire\":\"John Doe\",\"banque\":\"Attijari\","
                + "\"code_banque\":\"007\",\"code_ville\":\"780\",\"numero_compte\":\"1234567890123\","
                + "\"cle_rib\":\"45\",\"devise\":\"MAD\"}}"
        );
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        AffiliationDocumentValidationResponse response = validationService.validateDocument(
            "ribDocument",
            new MockMultipartFile("file", "rib.png", "image/png", new byte[] {1, 2, 3})
        );

        assertThat(response.status()).isEqualTo("VALID");
        assertThat(response.ribExtraction()).isNotNull();
        assertThat(response.ribExtraction().titulaire()).isEqualTo("John Doe");
        assertThat(response.ribExtraction().rib()).isEqualTo("007 780 1234567890123 45 MAD");
    }

    @Test
    void ribDocumentMismatchIsMarkedInvalid() throws IOException {
        String baseUrl = startFakeApi(
            "/api/process",
            200,
            "{\"success\":true,\"document_class\":\"CIN\"}"
        );
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        AffiliationDocumentValidationResponse response = validationService.validateDocument(
            "ribDocument",
            new MockMultipartFile("file", "rib.png", "image/png", new byte[] {1, 2, 3})
        );

        assertThat(response.status()).isEqualTo("INVALID");
    }

    @Test
    void ribExtractionUsesExplicitRibWhenAvailable() throws IOException {
        String baseUrl = startFakeApi(
            "/api/process",
            200,
            "{\"success\":true,\"document_class\":\"RIB\",\"extraction\":{\"rib\":\"007-780-abc-45\"}}"
        );
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        AffiliationDocumentValidationResponse response = validationService.validateDocument(
            "ribDocument",
            new MockMultipartFile("file", "rib.png", "image/png", new byte[] {1, 2, 3})
        );

        assertThat(response.ribExtraction().rib()).isEqualTo("007780ABC45");
    }

    @Test
    void ribExtractionReturnsNullWhenNoUsableFieldsPresent() throws IOException {
        String baseUrl = startFakeApi(
            "/api/process",
            200,
            "{\"success\":true,\"document_class\":\"RIB\",\"extraction\":{}}"
        );
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        AffiliationDocumentValidationResponse response = validationService.validateDocument(
            "ribDocument",
            new MockMultipartFile("file", "rib.png", "image/png", new byte[] {1, 2, 3})
        );

        assertThat(response.ribExtraction()).isNull();
    }

    @Test
    void skipsEmptyEntriesInBatchValidationAndValidatesTheRest() throws IOException {
        String baseUrl = startFakeApi(
            "/api/validate",
            200,
            "{\"valid\":true,\"detected_type\":\"CIN\"}"
        );
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        validationService.validateUploadedDocumentsOrThrow(
            Map.of(
                "cinDocument", new MockMultipartFile("file", "cin.png", "image/png", new byte[] {1}),
                "ribDocument", new MockMultipartFile("empty", "", "image/png", new byte[0])
            )
        );
    }

    @Test
    void throwsIllegalStateWhenApiUnreachable() {
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, "http://127.0.0.1:1", "");

        assertThatThrownBy(() ->
            validationService.validateDocument(
                "cinDocument",
                new MockMultipartFile("file", "cin.png", "image/png", new byte[] {1, 2, 3})
            )
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsIllegalStateWhenProcessingApiUnreachable() {
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, "http://127.0.0.1:1", "");

        assertThatThrownBy(() ->
            validationService.validateDocument(
                "ribDocument",
                new MockMultipartFile("file", "rib.png", "image/png", new byte[] {1, 2, 3})
            )
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsIllegalArgumentWhenProcessingApiReturnsClientError() throws IOException {
        String baseUrl = startFakeApi("/api/process", 400, "{\"error\":\"document illisible\"}");
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        assertThatThrownBy(() ->
            validationService.validateDocument(
                "ribDocument",
                new MockMultipartFile("file", "rib.png", "image/png", new byte[] {1, 2, 3})
            )
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("document illisible");
    }

    @Test
    void throwsIllegalStateWhenProcessingApiReturnsServerError() throws IOException {
        String baseUrl = startFakeApi("/api/process", 500, "{\"error\":\"crash extraction\"}");
        AffiliationDocumentValidationService validationService =
            new AffiliationDocumentValidationService(true, baseUrl, "");

        assertThatThrownBy(() ->
            validationService.validateDocument(
                "ribDocument",
                new MockMultipartFile("file", "rib.png", "image/png", new byte[] {1, 2, 3})
            )
        ).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("crash extraction");
    }

    @Test
    void resolvesExpectedTypeForEveryKnownSupportedDocumentKey() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.jpg", "image/jpeg", new byte[] {1});

        assertThat(serviceDisabled.validateDocument("patenteDocument", file).expectedType()).isEqualTo("PATENTE");
        assertThat(serviceDisabled.validateDocument("statutsDocument", file).expectedType()).isEqualTo("STATUS");
        assertThat(serviceDisabled.validateDocument("rcDocument", file).expectedType()).isEqualTo("RC");
        assertThat(serviceDisabled.validateDocument("iceDocument", file).expectedType()).isEqualTo("ICE");
        assertThat(serviceDisabled.validateDocument("cinRepresentantDocument", file).expectedType()).isEqualTo("CIN");
        assertThat(serviceDisabled.validateDocument("attestationAeDocument", file).expectedType())
            .isEqualTo("CARTE_AE");
        assertThat(serviceDisabled.validateDocument("cinSignataireDocument", file).expectedType()).isEqualTo("CIN");
        assertThat(serviceDisabled.validateDocument("ribDocument", file).expectedType()).isEqualTo("RIB");
    }

    @Test
    void resolvesUnsupportedDocumentKeysWithoutExpectedType() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.jpg", "image/jpeg", new byte[] {1});

        assertThat(serviceDisabled.validateDocument("pvAssociationDocument", file).supported()).isFalse();
        assertThat(serviceDisabled.validateDocument("listeMembresDocument", file).supported()).isFalse();
    }
}
