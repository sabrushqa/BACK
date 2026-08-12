package com.example.demo.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Reproduit le bug reellement observe en navigateur : "422 Unprocessable
 * Content" intermittent sur /api/merchant/chatbot/message, avec dans les
 * logs v2 "WARNING: Unsupported upgrade request." juste avant chaque 422.
 *
 * Root cause : RestClient.builder() par defaut construit un
 * JdkClientHttpRequestFactory dont le java.net.http.HttpClient sous-jacent
 * NEGOCIE HTTP/2 (tente une upgrade h2c en clair) des qu'il reutilise une
 * connexion garde ouverte (keep-alive). uvicorn (h11, HTTP/1.1 strict) ne
 * sait pas repondre a cette upgrade, logue l'avertissement, et la requete
 * suivante sur cette meme connexion mal negociee est mal parsee -> 422.
 *
 * Le faux serveur HTTP (com.sun.net.httpserver, utilise par
 * ChatbotProxyControllerHttpIntegrationTest) NE reproduit PAS ce bug — il
 * n'implemente pas le meme protocole de negociation que h11. Seul un appel
 * repete contre le VRAI uvicorn (port 9100, doit tourner localement) via le
 * VRAI bean RestClient de ChatbotProxyController (donc avec reutilisation de
 * connexion reelle) peut prouver que le fix (Version.HTTP_1_1 explicite)
 * elimine le probleme. Ignore silencieusement si v2 n'est pas up (verif
 * manuelle, pas une garantie CI).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestJwtSupport.class)
class ChatbotProxyControllerRealV2ConnectionReuseTest {

    private static final int REAL_V2_PORT = 9100;
    private static final String REAL_SHARED_TOKEN = "dev-local-chatbot-outbound-token";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @DynamicPropertySource
    static void chatbotProperties(DynamicPropertyRegistry registry) {
        registry.add("app.chatbot.base-url", () -> "http://127.0.0.1:" + REAL_V2_PORT);
        registry.add("app.chatbot.token", () -> REAL_SHARED_TOKEN);
    }

    @BeforeEach
    void assumeRealV2IsRunning() {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", REAL_V2_PORT), 500);
        } catch (IOException e) {
            Assumptions.abort("v2 (uvicorn) n'est pas demarre sur le port " + REAL_V2_PORT + " — verification manuelle uniquement, pas de couverture CI.");
        }
    }

    @AfterEach
    void cleanUp() {
        utilisateurRepository.findByEmailIgnoreCase("connreuse.integration@test.lanacash.ma")
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
    void tenSequentialCallsThroughRealV2NeverReturn422() throws IOException, InterruptedException {
        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail("connreuse.integration@test.lanacash.ma");
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(true);
        merchantUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercantRepository.save(commercant);

        String bearer = "Bearer " + tokenFor(merchantUser);

        for (int i = 0; i < 10; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/merchant/chatbot/message"))
                .header("Content-Type", "application/json")
                .header("Authorization", bearer)
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"test connexion reutilisee " + i + "\"}"))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode())
                .as("appel #%d ne doit jamais renvoyer 422 (bug de negociation HTTP/2 vs uvicorn)", i)
                .isEqualTo(200);
        }
    }
}
