package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class ChurnModelClientTest {

    private static final String HMAC_SECRET = "test-hmac-secret-with-at-least-32-characters";

    @Test
    void predictSendsMerchantFeaturesAsJsonBodyOverHttp11() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        AtomicReference<String> receivedToken = new AtomicReference<>();
        AtomicReference<String> receivedTimestamp = new AtomicReference<>();
        AtomicReference<String> receivedNonce = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/churn/predict", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            receivedTimestamp.set(exchange.getRequestHeaders().getFirst("X-Lana-Timestamp"));
            receivedNonce.set(exchange.getRequestHeaders().getFirst("X-Lana-Nonce"));
            receivedSignature.set(exchange.getRequestHeaders().getFirst("X-Lana-Signature"));
            byte[] response = """
                {"commercant_id":42,"score_risque":62.5,"niveau_risque":"MOYEN",
                 "raisons":["Chiffre d'affaires en baisse"],
                 "action_recommandee":"Planifier une relance"}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            ChurnModelClient client = new ChurnModelClient(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                HMAC_SECRET,
                "hmac"
            );
            ChurnModelClient.RiskPredictionResponse response = client.predict(
                new ChurnModelClient.MerchantFeaturesRequest(
                    42, 300.0, 1200.0, 4200.0, 2, 8, 24, 150.0,
                    0.1, 1, -0.2, 2, 1, "Mode", "Casablanca-Settat"
                )
            );

            JsonNode json = new ObjectMapper().readTree(receivedBody.get());
            assertThat(receivedContentType.get()).startsWith("application/json");
            assertThat(receivedToken.get()).isNull();
            assertThat(receivedTimestamp.get()).isNotBlank();
            assertThat(receivedNonce.get()).hasSizeGreaterThanOrEqualTo(16);
            assertThat(receivedSignature.get()).isEqualTo(expectedSignature(
                receivedTimestamp.get(), receivedNonce.get(), receivedBody.get()
            ));
            assertThat(json.get("commercant_id").asLong()).isEqualTo(42);
            assertThat(json.get("ca_7j").asDouble()).isEqualTo(300.0);
            assertThat(json.get("transactions_30j").asInt()).isEqualTo(8);
            assertThat(json.get("secteur").asText()).isEqualTo("Mode");
            assertThat(json.properties()).extracting(java.util.Map.Entry::getKey).containsExactlyInAnyOrderElementsOf(Set.of(
                "commercant_id", "ca_7j", "ca_30j", "ca_90j",
                "transactions_7j", "transactions_30j", "transactions_90j",
                "panier_moyen_30j", "taux_refus_30j", "jours_sans_transaction",
                "variation_ca_30j", "nombre_tpe", "nombre_reclamations_90j",
                "secteur", "region"
            ));
            assertThat(response.scoreRisque()).isEqualTo(62.5);
            assertThat(response.niveauRisque()).isEqualTo("MOYEN");
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 422, 500})
    void predictTurnsRemoteErrorsIntoAnExplicitServiceFailure(int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/churn/predict", exchange -> {
            byte[] response = "{\"detail\":\"test failure\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            ChurnModelClient client = new ChurnModelClient(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "internal-token",
                "development"
            );

            assertThatThrownBy(() -> client.predict(sampleRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponible");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void predictFailsClearlyWhenModelServiceIsStopped() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int unusedPort = server.getAddress().getPort();
        server.start();
        server.stop(0);
        ChurnModelClient client = new ChurnModelClient(
            RestClient.builder(), "http://127.0.0.1:" + unusedPort, "internal-token", "development"
        );

        assertThatThrownBy(() -> client.predict(sampleRequest()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("indisponible");
    }

    @Test
    void hmacModeRejectsWeakKeysAndPlainHttpOutsideLoopback() {
        assertThatThrownBy(() -> new ChurnModelClient(
            RestClient.builder(), "https://model.internal", "short", "hmac"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32 caractères");

        assertThatThrownBy(() -> new ChurnModelClient(
            RestClient.builder(), "http://model.internal", HMAC_SECRET, "hmac"
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
    }

    private ChurnModelClient.MerchantFeaturesRequest sampleRequest() {
        return new ChurnModelClient.MerchantFeaturesRequest(
            42, 300.0, 1200.0, 4200.0, 2, 8, 24, 150.0,
            0.1, 1, -0.2, 2, 1, "Mode", "Casablanca-Settat"
        );
    }

    private String expectedSignature(String timestamp, String nonce, String body) throws Exception {
        String bodyDigest = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))
        );
        String canonical = String.join("\n", "v1", "POST", "/v1/churn/predict", timestamp, nonce, bodyDigest);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
