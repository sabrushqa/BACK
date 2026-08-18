package com.example.demo.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client REST vers le service Python "lana-merchant-intelligence" (scoring
 * du risque d'abandon commerçant) — voir lana-merchant-intelligence/src/
 * lana_merchant_intelligence/api.py::predict_churn.
 */
@Service
public class ChurnModelClient {

    private static final String DEVELOPMENT_TOKEN = "change-me-lana-churn-internal-token";
    // Chemin fixe du contrat d'API interne avec lana-merchant-intelligence (pas
    // une valeur d'environnement) — la partie reellement configurable est deja
    // externalisee via app.churn-model.base-url. Faux positif Sonar S1075, qui
    // signale par principe toute chaine commencant par "/".
    private static final String PREDICT_PATH = "/v1/churn/predict"; // NOSONAR java:S1075
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String authenticationSecret;
    private final boolean hmacMode;

    public ChurnModelClient(
        RestClient.Builder restClientBuilder,
        @Value("${app.churn-model.base-url:http://localhost:8091}") String baseUrl,
        @Value("${app.churn-model.internal-token:change-me-lana-churn-internal-token}") String internalToken,
        @Value("${app.churn-model.security-mode:development}") String securityMode
    ) {
        String normalizedMode = securityMode == null ? "" : securityMode.trim().toLowerCase(Locale.ROOT);
        if (!List.of("development", "hmac").contains(normalizedMode)) {
            throw new IllegalArgumentException("app.churn-model.security-mode doit valoir development ou hmac");
        }
        this.hmacMode = "hmac".equals(normalizedMode);
        this.authenticationSecret = internalToken == null ? "" : internalToken;
        if (hmacMode && (authenticationSecret.length() < 32
            || DEVELOPMENT_TOKEN.equals(authenticationSecret)
            || authenticationSecret.toLowerCase(Locale.ROOT).startsWith("change-me"))) {
            throw new IllegalArgumentException("Le secret HMAC de production doit contenir au moins 32 caractères");
        }
        requireSecureProductionUrl(baseUrl);
        // Uvicorn n'accepte pas la negociation h2c envoyee par le client JDK
        // par defaut. Cette tentative d'upgrade faisait arriver le POST sans
        // corps (422) ou comme requete HTTP invalide (400). Le service local
        // communique donc explicitement en HTTP/1.1.
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = restClientBuilder
            .baseUrl(stripTrailingSlash(baseUrl))
            .requestFactory(requestFactory)
            .build();
    }

    public record MerchantFeaturesRequest(
        @JsonProperty("commercant_id") long commercantId,
        @JsonProperty("ca_7j") double ca7j,
        @JsonProperty("ca_30j") double ca30j,
        @JsonProperty("ca_90j") double ca90j,
        @JsonProperty("transactions_7j") int transactions7j,
        @JsonProperty("transactions_30j") int transactions30j,
        @JsonProperty("transactions_90j") int transactions90j,
        @JsonProperty("panier_moyen_30j") double panierMoyen30j,
        @JsonProperty("taux_refus_30j") double tauxRefus30j,
        @JsonProperty("jours_sans_transaction") int joursSansTransaction,
        @JsonProperty("variation_ca_30j") double variationCa30j,
        @JsonProperty("nombre_tpe") int nombreTpe,
        @JsonProperty("nombre_reclamations_90j") int nombreReclamations90j,
        String secteur,
        String region
    ) {
    }

    public record RiskPredictionResponse(
        @JsonProperty("commercant_id") long commercantId,
        @JsonProperty("score_risque") double scoreRisque,
        @JsonProperty("niveau_risque") String niveauRisque,
        List<String> raisons,
        @JsonProperty("action_recommandee") String actionRecommandee
    ) {
    }

    public RiskPredictionResponse predict(MerchantFeaturesRequest request) {
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            RestClient.RequestBodySpec requestSpec = restClient.post()
                .uri(PREDICT_PATH)
                // FastAPI attend explicitement un document JSON. Sans ce
                // content type, le RestClient de Spring 7 peut envoyer un
                // POST sans corps lorsqu'aucun convertisseur n'est retenu,
                // ce qui faisait rejeter tous les commerçants avec un 422
                // "body missing".
                .contentType(MediaType.APPLICATION_JSON);
            if (hmacMode) {
                String timestamp = Long.toString(Instant.now().getEpochSecond());
                String nonce = UUID.randomUUID().toString();
                requestSpec.headers(headers -> {
                    headers.set("X-Lana-Timestamp", timestamp);
                    headers.set("X-Lana-Nonce", nonce);
                    headers.set("X-Lana-Signature", calculateSignature(timestamp, nonce, jsonBody));
                });
            } else {
                requestSpec.header("X-Internal-Token", authenticationSecret);
            }
            return requestSpec
                .body(jsonBody)
                .retrieve()
                .body(RiskPredictionResponse.class);
        } catch (RestClientException | JsonProcessingException exception) {
            throw new IllegalStateException(
                "Le service lana-merchant-intelligence est indisponible, impossible de calculer le score de risque.",
                exception
            );
        }
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String calculateSignature(String timestamp, String nonce, String jsonBody) {
        try {
            String bodyDigest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(jsonBody.getBytes(StandardCharsets.UTF_8))
            );
            String canonical = String.join("\n", "v1", "POST", PREDICT_PATH, timestamp, nonce, bodyDigest);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authenticationSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Impossible de signer la requête de scoring", exception);
        }
    }

    private void requireSecureProductionUrl(String baseUrl) {
        if (!hmacMode) {
            return;
        }
        URI uri = URI.create(baseUrl);
        String host = uri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host)
            || "::1".equals(host);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopback) {
            throw new IllegalArgumentException("HTTPS est obligatoire pour le modèle en mode hmac hors loopback");
        }
    }
}
