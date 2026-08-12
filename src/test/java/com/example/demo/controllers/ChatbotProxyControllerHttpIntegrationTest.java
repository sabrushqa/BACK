package com.example.demo.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Reproduit EXACTEMENT ce que fait le navigateur : un vrai appel HTTP vers
 * /api/merchant/chatbot/message, a travers le VRAI filtre Spring Security
 * (JwtDecoder de test via TestJwtSupport, mais le meme filtre OAuth2 resource
 * server que la prod), pas un appel direct au controller comme
 * ChatbotProxyControllerTest (qui mocke MerchantAccessService et ne passe
 * jamais par Spring Security). Sert a verifier qu'un token valide ne produit
 * jamais le "corps vide / 401 inattendu" observe cote navigateur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestJwtSupport.class)
class ChatbotProxyControllerHttpIntegrationTest {
    // Pas de @Transactional ICI : ce test fait un vrai appel HTTP entrant sur
    // sa propre application (self-call) — s'il tournait dans la transaction
    // du test, le thread Tomcat qui traite la requete entrante attendrait une
    // connexion JDBC deja tenue par le thread de test (deadlock du pool
    // Hikari, deja observe une fois). Nettoyage manuel a la place.

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @DynamicPropertySource
    static void chatbotProperties(DynamicPropertyRegistry registry) {
        // Le port du faux chatbot n'est connu qu'apres son demarrage (port 0
        // = ephemere) — mais @DynamicPropertySource s'evalue AVANT la
        // construction du contexte, donc avant ChatbotProxyController. On
        // demarre le faux serveur ici, statiquement, pour connaitre son port
        // a temps.
        try {
            SharedFakeChatbot.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        registry.add("app.chatbot.base-url", () -> "http://127.0.0.1:" + SharedFakeChatbot.port());
        registry.add("app.chatbot.token", () -> "secret-token");
    }

    @AfterEach
    void cleanUp() {
        utilisateurRepository.findByEmailIgnoreCase("http.integration@test.lanacash.ma")
            .ifPresent(user -> {
                commercantRepository.findByUtilisateur_Id(user.getId())
                    .ifPresent(commercantRepository::delete);
                utilisateurRepository.delete(user);
            });
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void realHttpCallWithValidTokenForwardsMerchantIdAndSucceeds() throws IOException, InterruptedException {
        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail("http.integration@test.lanacash.ma");
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(true);
        merchantUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);
        Long commercantId = commercant.getIdCommercant();

        SharedFakeChatbot.expectResponse("{\"session_id\":\"s1\",\"response\":\"ok\",\"critical\":false,\"ticket\":null}");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/merchant/chatbot/message"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + tokenFor(merchantUser))
            .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"bonjour\"}"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"response\":\"ok\"");
        assertThat(SharedFakeChatbot.lastReceivedBody()).contains("\"merchant_id\":\"" + commercantId + "\"");
    }

    @Test
    void realHttpCallWithoutTokenIsRejectedBeforeReachingController() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/merchant/chatbot/message"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"bonjour\"}"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    /**
     * Petit serveur HTTP local reutilise entre les tests de cette classe,
     * demarre depuis @DynamicPropertySource (contexte statique, avant
     * injection des beans) et arrete apres chaque test.
     */
    static final class SharedFakeChatbot {
        private static HttpServer server;
        private static volatile String responseBody = "{}";
        private static volatile String lastBody = "";

        static void start() throws IOException {
            if (server != null) {
                return;
            }
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/message", exchange -> {
                lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            });
            server.start();
        }

        static int port() {
            return server.getAddress().getPort();
        }

        static void expectResponse(String body) {
            responseBody = body;
        }

        static String lastReceivedBody() {
            return lastBody;
        }

        static void stop() {
            // Garde le serveur vivant entre les tests de cette classe (le port
            // est fige dans app.chatbot.base-url des le demarrage du contexte
            // Spring, partage par tous les @Test) — rien a arreter ici tant
            // que la JVM de test tourne.
        }
    }
}
