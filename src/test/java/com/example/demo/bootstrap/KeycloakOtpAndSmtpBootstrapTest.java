package com.example.demo.bootstrap;

import com.example.demo.services.KeycloakAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * KeycloakAdminService est desactive en test (app.keycloak.admin.enabled=false),
 * donc requireOtpForExistingUsers()/configureRealmSmtp() renvoient toujours
 * false sans appel HTTP reel — ces bootstraps peuvent donc etre exerces en
 * toute securite, y compris les branches ou la config SMTP est absente,
 * partielle ou complete.
 */
@SpringBootTest
class KeycloakOtpAndSmtpBootstrapTest {

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Test
    void otpBootstrapRunsWithoutThrowing() {
        new KeycloakOtpBootstrap(keycloakAdminService).run(null);
    }

    @Test
    void smtpBootstrapSkipsWhenHostIsBlank() {
        KeycloakSmtpBootstrap bootstrap = new KeycloakSmtpBootstrap(
            keycloakAdminService, "", "587", "", "", true, false, "", "Lana Cash", ""
        );

        bootstrap.run(null);
    }

    @Test
    void smtpBootstrapWarnsWhenCredentialsArePartial() {
        KeycloakSmtpBootstrap bootstrap = new KeycloakSmtpBootstrap(
            keycloakAdminService,
            "smtp.gmail.com",
            "587",
            "",
            "",
            true,
            false,
            "no-reply@lanacash.ma",
            "Lana Cash",
            ""
        );

        bootstrap.run(null);
    }

    @Test
    void smtpBootstrapRunsWithFullConfiguration() {
        KeycloakSmtpBootstrap bootstrap = new KeycloakSmtpBootstrap(
            keycloakAdminService,
            "smtp.gmail.com",
            "587",
            "no-reply@lanacash.ma",
            "app-password",
            true,
            false,
            "",
            "Lana Cash",
            "support@lanacash.ma"
        );

        bootstrap.run(null);
    }
}
