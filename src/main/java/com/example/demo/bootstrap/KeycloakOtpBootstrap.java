package com.example.demo.bootstrap;

import com.example.demo.services.KeycloakAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Marque les comptes Keycloak existants pour configurer un TOTP lorsque
 * l'OTP/MFA est activé côté application.
 */
@Component
@Order(7)
@ConditionalOnProperty(
    name = "app.keycloak.otp.required",
    havingValue = "true"
)
public class KeycloakOtpBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakOtpBootstrap.class);

    private final KeycloakAdminService keycloakAdminService;

    public KeycloakOtpBootstrap(KeycloakAdminService keycloakAdminService) {
        this.keycloakAdminService = keycloakAdminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!keycloakAdminService.requireOtpForExistingUsers()) {
            LOGGER.warn("OTP Keycloak non appliqué aux comptes existants.");
        }
    }
}
