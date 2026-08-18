package com.example.demo.bootstrap;

import com.example.demo.services.KeycloakAdminService;
import com.example.demo.util.UrlUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Pousse la configuration du client public Keycloak ({@code portail-affiliation})
 * au démarrage : redirect URIs, web origins, PKCE, public client. Sans cette
 * configuration, {@code execute-actions-email} rejette tout {@code redirect_uri}
 * avec un 400 "Invalid redirect uri" et le flux mot de passe oublié échoue.
 */
@Component
@Order(6)
@ConditionalOnProperty(
    name = "app.keycloak.client.bootstrap.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class KeycloakClientBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakClientBootstrap.class);

    private final KeycloakAdminService keycloakAdminService;
    private final String frontendBaseUrl;
    private final String redirectUrisProperty;
    private final String webOriginsProperty;

    public KeycloakClientBootstrap(
        KeycloakAdminService keycloakAdminService,
        @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl,
        @Value("${app.keycloak.client.redirect-uris:}") String redirectUrisProperty,
        @Value("${app.keycloak.client.web-origins:}") String webOriginsProperty
    ) {
        this.keycloakAdminService = keycloakAdminService;
        this.frontendBaseUrl = frontendBaseUrl;
        this.redirectUrisProperty = redirectUrisProperty;
        this.webOriginsProperty = webOriginsProperty;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> redirectUris = resolveRedirectUris();
        List<String> webOrigins = resolveWebOrigins();

        boolean configured = keycloakAdminService.configureClient(redirectUris, webOrigins);
        if (!configured) {
            LOGGER.warn(
                "Client Keycloak non synchronisé. Configurez les redirect URIs manuellement dans la console admin si nécessaire."
            );
        }
    }

    private List<String> resolveRedirectUris() {
        Set<String> uris = new LinkedHashSet<>(splitCsv(redirectUrisProperty));
        if (uris.isEmpty()) {
            String base = UrlUtils.stripTrailingSlash(frontendBaseUrl);
            uris.add(base + "/*");
            uris.add(base + "/login");
            uris.add(base + "/forgot-password");
            uris.add(base + "/activate-account");
            uris.add(base + "/silent-check-sso.html");
        }
        return new ArrayList<>(uris);
    }

    private List<String> resolveWebOrigins() {
        List<String> origins = splitCsv(webOriginsProperty);
        if (origins.isEmpty()) {
            return List.of(UrlUtils.stripTrailingSlash(frontendBaseUrl));
        }
        return origins;
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList();
    }

}
