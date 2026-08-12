package com.example.demo.controllers;

import com.example.demo.services.MerchantAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Relaie les appels du chat commerçant vers le microservice chatbot FastAPI.
 * Le token attendu par le chatbot (X-Chatbot-Token) reste cote serveur : le
 * navigateur n'authentifie cette route qu'avec son propre JWT Keycloak
 * (verifie par Spring Security sur /api/**), il n'a plus besoin de connaitre
 * le secret partage avec le chatbot.
 *
 * merchant_id : injecte ICI a partir du JWT deja verifie par Spring, JAMAIS
 * lu depuis ce que le navigateur envoie (le front n'envoie d'ailleurs
 * merchant_id nulle part actuellement — c'etait le vrai trou : le chatbot ne
 * savait pas a qui il parlait). Modele "trusted subsystem" : le chatbot fait
 * confiance a ce merchant_id parce qu'il vient de Spring, qui a deja
 * authentifie l'utilisateur — jamais d'un champ que le navigateur pourrait
 * forger.
 */
@RestController
@RequestMapping("/api/merchant/chatbot")
public class ChatbotProxyController {

    private static final String FALLBACK_MESSAGE_JSON =
        "{\"message\":\"Le chatbot est temporairement indisponible. Veuillez reessayer.\"}";

    private final RestClient restClient;
    private final String chatbotToken;
    private final MerchantAccessService merchantAccessService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatbotProxyController(
        @Value("${app.chatbot.base-url}") String chatbotBaseUrl,
        @Value("${app.chatbot.token}") String chatbotToken,
        MerchantAccessService merchantAccessService
    ) {
        // Version.HTTP_1_1 explicite : le JdkClientHttpRequestFactory par
        // defaut negocie HTTP/2 (tente une upgrade h2c en clair). uvicorn
        // (h11, HTTP/1.1 uniquement) ne sait pas repondre a cette upgrade —
        // "Unsupported upgrade request." cote uvicorn — et la requete suivante
        // sur la meme connexion pool ee est alors mal parsee, renvoyant un 422
        // qui n'a rien a voir avec le contenu du message (verifie : le meme
        // corps JSON envoye directement en HTTP/1.1 fonctionne toujours).
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .build();
        this.restClient = RestClient.builder()
            .baseUrl(chatbotBaseUrl)
            .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient))
            .build();
        this.chatbotToken = chatbotToken;
        this.merchantAccessService = merchantAccessService;
    }

    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> message(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody String rawJsonBody
    ) {
        String body = injectMerchantIdIntoJson(rawJsonBody, resolveMerchantId(authorizationHeader));
        return forward(
            restClient.post()
                .uri("/chat/message")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
        );
    }

    @PostMapping(value = "/session/prefill")
    public ResponseEntity<String> prefill(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam MultiValueMap<String, String> queryParams
    ) {
        String merchantId = resolveMerchantId(authorizationHeader);
        if (merchantId != null) {
            queryParams.set("merchant_id", merchantId);
        }
        return forward(
            restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/chat/session/prefill").queryParams(queryParams).build())
        );
    }

    @PostMapping(value = "/message-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> messageWithImage(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam("image") MultipartFile image,
        @RequestParam(value = "message", required = false) String message,
        @RequestParam(value = "session_id", required = false) String sessionId
    ) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("image", asResource(image));
        if (message != null) {
            form.add("message", message);
        }
        if (sessionId != null) {
            form.add("session_id", sessionId);
        }
        String merchantId = resolveMerchantId(authorizationHeader);
        if (merchantId != null) {
            form.add("merchant_id", merchantId);
        }
        return forward(
            restClient.post()
                .uri("/chat/message-with-image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
        );
    }

    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> audio(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam("audio") MultipartFile audio,
        @RequestParam(value = "session_id", required = false) String sessionId
    ) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", asResource(audio));
        if (sessionId != null) {
            form.add("session_id", sessionId);
        }
        String merchantId = resolveMerchantId(authorizationHeader);
        if (merchantId != null) {
            form.add("merchant_id", merchantId);
        }
        return forward(
            restClient.post()
                .uri("/chat/audio")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
        );
    }

    /**
     * Ne doit jamais faire planter la requete : un token absent/expire, ou un
     * utilisateur qui n'est pas un commerçant (staff testant la route, etc.),
     * degrade simplement vers "pas de merchant_id" — comme avant ce changement.
     */
    private String resolveMerchantId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        try {
            Long commercantId = merchantAccessService.resolveAuthenticatedCommercantId(authorizationHeader);
            return commercantId != null ? commercantId.toString() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String injectMerchantIdIntoJson(String rawJsonBody, String merchantId) {
        if (merchantId == null) {
            return rawJsonBody;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJsonBody);
            if (!(node instanceof ObjectNode objectNode)) {
                return rawJsonBody;
            }
            objectNode.put("merchant_id", merchantId);
            return objectMapper.writeValueAsString(objectNode);
        } catch (Exception exception) {
            // Corps illisible : on laisse passer tel quel, le chatbot renverra
            // sa propre erreur de validation plutot que de la masquer ici.
            return rawJsonBody;
        }
    }

    private ResponseEntity<String> forward(RestClient.RequestBodySpec spec) {
        try {
            return spec
                .header("X-Chatbot-Token", chatbotToken)
                .exchange((request, response) -> ResponseEntity
                    .status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            return ResponseEntity
                .status(HttpStatusCode.valueOf(502))
                .contentType(MediaType.APPLICATION_JSON)
                .body(FALLBACK_MESSAGE_JSON);
        }
    }

    private ByteArrayResource asResource(MultipartFile file) {
        try {
            return new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
        } catch (Exception exception) {
            throw new IllegalArgumentException("Impossible de lire le fichier envoye.", exception);
        }
    }
}
