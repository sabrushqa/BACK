package com.example.demo.bootstrap;

import com.example.demo.services.KeycloakAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * KeycloakAdminService est desactive en test (app.keycloak.admin.enabled=false),
 * donc configureClient() renvoie toujours false sans appel HTTP reel — ce
 * bootstrap peut donc etre exerce en toute securite pour verifier qu'il ne
 * plante pas, avec les valeurs par defaut (URLs derivees du frontend) et avec
 * des listes explicites de redirect URIs / web origins.
 */
@SpringBootTest
class KeycloakClientBootstrapTest {

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Test
    void runsWithDefaultFrontendDerivedUris() {
        KeycloakClientBootstrap bootstrap = new KeycloakClientBootstrap(
            keycloakAdminService,
            "http://localhost:4200/",
            "",
            ""
        );

        bootstrap.run(null);
    }

    @Test
    void runsWithExplicitRedirectUrisAndWebOrigins() {
        KeycloakClientBootstrap bootstrap = new KeycloakClientBootstrap(
            keycloakAdminService,
            "http://localhost:4200",
            "http://localhost:4200/*, http://localhost:4200/login",
            "http://localhost:4200"
        );

        bootstrap.run(null);
    }
}
