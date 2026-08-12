package com.example.demo.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client REST vers switch-monetique-service : demo ne cree/gere plus de stock TPE en
 * local, il consulte et fait affecter les TPE via cette API (la base Oracle du switch
 * reste l'unique source de verite pour l'inventaire des terminaux).
 *
 * Chaque requete est signee (HMAC-SHA256, meme algorithme que
 * MonetiqueSignatureService cote switch-monetique-service) : sans ces headers
 * (X-Monetique-Token / -Timestamp / -Request-Id / -Signature),
 * InternalApiTokenFilter rejette tout appel en 401. Ce point manquait
 * jusqu'ici — toute affectation TPE echouait silencieusement en production
 * (le controleur Spring catch RestClientException et remonte une erreur
 * generique, jamais visible en dev car les tests mockent ce client).
 */
@Service
public class SwitchMonetiqueClient {

    private final RestClient restClient;
    private final String internalToken;
    private final byte[] signatureSecret;

    public SwitchMonetiqueClient(
        RestClient.Builder restClientBuilder,
        @Value("${app.switch-monetique.base-url:http://localhost:8090}") String baseUrl,
        @Value("${MONETIQUE_INTERNAL_TOKEN:change-me-monetique-internal-token}") String internalToken,
        @Value("${MONETIQUE_SIGNATURE_SECRET:change-me-monetique-signature-secret-32chars}") String signatureSecret
    ) {
        this.internalToken = internalToken;
        this.signatureSecret = signatureSecret.getBytes(StandardCharsets.UTF_8);
        this.restClient = restClientBuilder
            .baseUrl(stripTrailingSlash(baseUrl))
            .requestInterceptor(this::signRequest)
            .build();
    }

    public record SwitchTpe(
        String idTpe,
        String idCommercant,
        String idPdv,
        String nature,
        String connectivite,
        boolean actif,
        BigDecimal plafondJournalier,
        LocalDateTime dateCreation
    ) {
    }

    public List<SwitchTpe> stockComplet() {
        return stock(null, false);
    }

    public List<SwitchTpe> stockDisponible(String nature) {
        return stock(nature, true);
    }

    private List<SwitchTpe> stock(String nature, boolean disponible) {
        try {
            String uri = UriComponentsBuilder.fromPath("/api/switch/tpes")
                .queryParamIfPresent("nature", java.util.Optional.ofNullable(nature))
                .queryParam("disponible", disponible)
                .build()
                .toUriString();
            SwitchTpe[] result = restClient.get().uri(uri).retrieve().body(SwitchTpe[].class);
            return result == null ? List.of() : List.of(result);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                "Le service switch-monetique est indisponible, impossible de consulter le stock de TPE.",
                exception
            );
        }
    }

    public java.util.Optional<SwitchTpe> parId(String idTpe) {
        try {
            return java.util.Optional.ofNullable(
                restClient.get().uri("/api/switch/tpes/{id}", idTpe).retrieve().body(SwitchTpe.class)
            );
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound notFound) {
            return java.util.Optional.empty();
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                "Le service switch-monetique est indisponible, impossible de consulter la référence TPE.",
                exception
            );
        }
    }

    public SwitchTpe activer(String idTpe) {
        return restClient.put().uri("/api/switch/tpes/{id}/activate", idTpe).retrieve().body(SwitchTpe.class);
    }

    public SwitchTpe desactiver(String idTpe) {
        return restClient.put().uri("/api/switch/tpes/{id}/deactivate", idTpe).retrieve().body(SwitchTpe.class);
    }

    /**
     * @deprecated Utiliser la surcharge avec nomCommercial/typeAffiliation/region :
     * sans ces infos, switch-monetique-service se contente d'affecter le TPE sans
     * créer/mettre à jour la fiche Commercant côté Oracle.
     */
    @Deprecated
    public SwitchTpe affecter(String idTpe, String idCommercant, String idPdv) {
        return affecter(idTpe, idCommercant, idPdv, null, null, null);
    }

    public SwitchTpe affecter(
        String idTpe,
        String idCommercant,
        String idPdv,
        String nomCommercial,
        String typeAffiliation,
        String region
    ) {
        return restClient.put()
            .uri("/api/switch/tpes/{id}/assign", idTpe)
            .body(new AssignBody(idCommercant, idPdv, nomCommercial, typeAffiliation, region))
            .retrieve()
            .body(SwitchTpe.class);
    }

    private record AssignBody(
        String idCommercant,
        String idPdv,
        String nomCommercial,
        String typeAffiliation,
        String region
    ) {
    }

    /**
     * Change le point de vente d'un TPE deja affecte (le commercant re-range
     * son propre materiel entre ses PDV) — ne touche pas idCommercant, contrairement
     * a affecter().
     */
    public SwitchTpe mettreAJourPdv(String idTpe, String idPdv) {
        return restClient.put()
            .uri("/api/switch/tpes/{id}/pdv", idTpe)
            .body(new UpdatePdvBody(idPdv))
            .retrieve()
            .body(SwitchTpe.class);
    }

    private record UpdatePdvBody(String idPdv) {
    }

    /**
     * Signe chaque requete sortante exactement comme MonetiqueSignatureService::
     * verifyRequest l'attend cote switch-monetique-service :
     * HMAC-SHA256(secret, timestamp + "." + requestId + "." + method + "." + path).
     */
    private ClientHttpResponse signRequest(
        HttpRequest request,
        byte[] body,
        ClientHttpRequestExecution execution
    ) throws IOException {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String requestId = UUID.randomUUID().toString();
        String path = request.getURI().getRawPath();
        String signature = hmacHex(timestamp + "." + requestId + "." + request.getMethod().name() + "." + path);

        request.getHeaders().add("X-Monetique-Token", internalToken);
        request.getHeaders().add("X-Monetique-Timestamp", timestamp);
        request.getHeaders().add("X-Monetique-Request-Id", requestId);
        request.getHeaders().add("X-Monetique-Signature", signature);

        return execution.execute(request, body);
    }

    private String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signatureSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Calcul HMAC impossible.", exception);
        }
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
