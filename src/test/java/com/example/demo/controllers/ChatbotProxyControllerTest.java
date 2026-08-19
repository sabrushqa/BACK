package com.example.demo.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.services.MerchantAccessService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Tests unitaires purs du relais vers le microservice chatbot: demarre un
 * faux serveur HTTP local (aucun mock library necessaire pour le HTTP) pour
 * verifier que chaque route relaie correctement la reponse, et que toute
 * erreur reseau retombe sur le message de secours plutot que de faire
 * planter la requete. MerchantAccessService est mocke (Mockito) car c'est un
 * bean lourd (nombreux repositories) — seul son resultat de resolution de
 * merchant_id nous interesse ici, pas son implementation reelle.
 */
class ChatbotProxyControllerTest {

    private static final String AUTH_HEADER = "Bearer some-jwt";

    private HttpServer fakeChatbotServer;
    private MerchantAccessService merchantAccessService;

    @BeforeEach
    void setUpMerchantAccessService() {
        merchantAccessService = mock(MerchantAccessService.class);
    }

    @AfterEach
    void stopFakeChatbotServer() {
        if (fakeChatbotServer != null) {
            fakeChatbotServer.stop(0);
        }
    }

    private Map<String, String> lastRequestHeaders;
    private String lastRequestBody;

    private String startFakeChatbot(String path, int statusCode, String body) throws IOException {
        lastRequestHeaders = new LinkedHashMap<>();
        fakeChatbotServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeChatbotServer.createContext(path, exchange -> {
            lastRequestHeaders.put(
                "X-Chatbot-Token",
                exchange.getRequestHeaders().getFirst("X-Chatbot-Token")
            );
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        fakeChatbotServer.start();
        return "http://127.0.0.1:" + fakeChatbotServer.getAddress().getPort();
    }

    @Test
    void forwardsMessageAndReturnsChatbotResponse() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message", 200, "{\"reply\":\"bonjour\"}");
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        var response = controller.message(null, "{\"message\":\"salut\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"reply\":\"bonjour\"}");
        assertThat(lastRequestHeaders.get("X-Chatbot-Token")).isEqualTo("secret-token");
    }

    @Test
    void injectsAuthenticatedMerchantIdIntoMessageBody() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message", 200, "{\"reply\":\"bonjour\"}");
        when(merchantAccessService.resolveAuthenticatedCommercantId(AUTH_HEADER)).thenReturn(42L);
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        // Le navigateur n'envoie pas merchant_id (comportement reel actuel) —
        // le proxy doit quand meme l'injecter a partir du JWT.
        controller.message(AUTH_HEADER, "{\"message\":\"salut\"}");

        assertThat(lastRequestBody).contains("\"merchant_id\":\"42\"");
    }

    @Test
    void overridesClientSuppliedMerchantIdRatherThanTrustingIt() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message", 200, "{\"reply\":\"bonjour\"}");
        when(merchantAccessService.resolveAuthenticatedCommercantId(AUTH_HEADER)).thenReturn(42L);
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        // Un navigateur malveillant qui forgerait quand meme merchant_id ne
        // doit pas pouvoir usurper un autre commerçant.
        controller.message(AUTH_HEADER, "{\"message\":\"salut\",\"merchant_id\":\"999\"}");

        assertThat(lastRequestBody).contains("\"merchant_id\":\"42\"");
        assertThat(lastRequestBody).doesNotContain("999");
    }

    @Test
    void doesNotInjectMerchantIdWhenNotAuthenticated() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message", 200, "{\"reply\":\"bonjour\"}");
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        controller.message(null, "{\"message\":\"salut\"}");

        assertThat(lastRequestBody).doesNotContain("merchant_id");
    }

    @Test
    void forwardsSessionPrefillQueryParamsWithMerchantIdInjected() throws IOException {
        String baseUrl = startFakeChatbot("/chat/session/prefill", 200, "{\"ok\":true}");
        when(merchantAccessService.resolveAuthenticatedCommercantId(AUTH_HEADER)).thenReturn(42L);
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("session_id", "abc123");

        var response = controller.prefill(AUTH_HEADER, queryParams);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"ok\":true}");
        verify(merchantAccessService).resolveAuthenticatedCommercantId(AUTH_HEADER);
    }

    @Test
    void forwardsMessageWithImageAndOptionalFields() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message-with-image", 200, "{\"reply\":\"ok\"}");
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        MockMultipartFile image = new MockMultipartFile("image", "photo.png", "image/png", new byte[] {1, 2, 3});

        var response = controller.messageWithImage(null, image, "Voici une photo", "session-42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"reply\":\"ok\"}");
    }

    @Test
    void forwardsMessageWithImageWithoutOptionalFields() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message-with-image", 200, "{\"reply\":\"ok\"}");
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        MockMultipartFile image = new MockMultipartFile("image", "photo.png", "image/png", new byte[] {1, 2, 3});

        var response = controller.messageWithImage(null, image, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void forwardsAudioMessage() throws IOException {
        String baseUrl = startFakeChatbot("/chat/audio", 200, "{\"reply\":\"ok\"}");
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[] {1, 2, 3});

        var response = controller.audio(null, audio, "session-7");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"reply\":\"ok\"}");
    }

    @Test
    void returnsFallbackMessageWhenChatbotIsUnreachable() {
        ChatbotProxyController controller =
            new ChatbotProxyController("http://127.0.0.1:1", "secret-token", merchantAccessService);

        var response = controller.message(null, "{\"message\":\"salut\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).contains("temporairement indisponible");
    }

    @Test
    void injectsMerchantPdvIdForSousCommercantIntoMessageBody() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message", 200, "{\"reply\":\"bonjour\"}");
        when(merchantAccessService.resolveAuthenticatedCommercantId(AUTH_HEADER)).thenReturn(42L);
        when(merchantAccessService.resolveAuthenticatedPdvIdForSousCommercant(AUTH_HEADER)).thenReturn(7L);
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        // Un sous-commerçant garde le merchant_id du commerçant PARENT (son
        // identite), mais doit AUSSI porter merchant_pdv_id : sans ca, le
        // profil recupere par le chatbot (ChatbotMerchantProfileController)
        // couvre tout le commerçant parent au lieu du seul PDV du
        // sous-commerçant.
        controller.message(AUTH_HEADER, "{\"message\":\"salut\"}");

        assertThat(lastRequestBody).contains("\"merchant_id\":\"42\"");
        assertThat(lastRequestBody).contains("\"merchant_pdv_id\":\"7\"");
    }

    @Test
    void doesNotInjectMerchantPdvIdForRegularCommercant() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message", 200, "{\"reply\":\"bonjour\"}");
        when(merchantAccessService.resolveAuthenticatedCommercantId(AUTH_HEADER)).thenReturn(42L);
        when(merchantAccessService.resolveAuthenticatedPdvIdForSousCommercant(AUTH_HEADER)).thenReturn(null);
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        controller.message(AUTH_HEADER, "{\"message\":\"salut\"}");

        assertThat(lastRequestBody).doesNotContain("merchant_pdv_id");
    }

    @Test
    void forwardsSessionPrefillWithMerchantPdvIdInjectedForSousCommercant() throws IOException {
        String baseUrl = startFakeChatbot("/chat/session/prefill", 200, "{\"ok\":true}");
        when(merchantAccessService.resolveAuthenticatedCommercantId(AUTH_HEADER)).thenReturn(42L);
        when(merchantAccessService.resolveAuthenticatedPdvIdForSousCommercant(AUTH_HEADER)).thenReturn(7L);
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("session_id", "abc123");

        var response = controller.prefill(AUTH_HEADER, queryParams);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(merchantAccessService).resolveAuthenticatedPdvIdForSousCommercant(AUTH_HEADER);
    }

    @Test
    void gracefullyIgnoresMerchantResolutionFailure() throws IOException {
        String baseUrl = startFakeChatbot("/chat/message", 200, "{\"reply\":\"bonjour\"}");
        when(merchantAccessService.resolveAuthenticatedCommercantId(any()))
            .thenThrow(new RuntimeException("Keycloak down"));
        ChatbotProxyController controller = new ChatbotProxyController(baseUrl, "secret-token", merchantAccessService);

        var response = controller.message(AUTH_HEADER, "{\"message\":\"salut\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastRequestBody).doesNotContain("merchant_id");
    }
}
