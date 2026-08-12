package com.example.demo.bootstrap;

import com.example.demo.services.KeycloakAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Pousse la configuration SMTP du realm Keycloak au démarrage de l'application,
 * en reutilisant les credentials Gmail definis dans application.properties
 * (spring.mail.*). Sans cette configuration, les e-mails
 * {@code execute-actions-email} (UPDATE_PASSWORD, VERIFY_EMAIL, etc.) ne
 * partent pas et le flux "mot de passe oublié" renvoie une 503.
 */
@Component
@Order(5)
@ConditionalOnProperty(
    name = "app.keycloak.smtp.bootstrap.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class KeycloakSmtpBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakSmtpBootstrap.class);

    private final KeycloakAdminService keycloakAdminService;
    private final String smtpHost;
    private final String smtpPort;
    private final String smtpUsername;
    private final String smtpPassword;
    private final boolean smtpStartTls;
    private final boolean smtpSsl;
    private final String smtpFrom;
    private final String smtpFromDisplayName;
    private final String smtpReplyTo;

    public KeycloakSmtpBootstrap(
        KeycloakAdminService keycloakAdminService,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${spring.mail.port:587}") String smtpPort,
        @Value("${spring.mail.username:}") String smtpUsername,
        @Value("${spring.mail.password:}") String smtpPassword,
        @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") boolean smtpStartTls,
        @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") boolean smtpSsl,
        @Value("${app.keycloak.smtp.from:}") String smtpFromOverride,
        @Value("${app.keycloak.smtp.from-display-name:Lana Cash}") String smtpFromDisplayName,
        @Value("${app.keycloak.smtp.reply-to:}") String smtpReplyToOverride
    ) {
        this.keycloakAdminService = keycloakAdminService;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.smtpStartTls = smtpStartTls;
        this.smtpSsl = smtpSsl;
        this.smtpFrom = smtpFromOverride == null || smtpFromOverride.isBlank()
            ? smtpUsername
            : smtpFromOverride;
        this.smtpFromDisplayName = smtpFromDisplayName;
        this.smtpReplyTo = smtpReplyToOverride == null || smtpReplyToOverride.isBlank()
            ? this.smtpFrom
            : smtpReplyToOverride;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (smtpHost == null || smtpHost.isBlank() || smtpFrom == null || smtpFrom.isBlank()) {
            LOGGER.warn(
                "Configuration SMTP Keycloak ignorée au démarrage: spring.mail.host='{}' (vide ?) | from='{}' (vide ?). "
                    + "Renseigne SPRING_MAIL_HOST, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD et eventuellement "
                    + "APP_KEYCLOAK_SMTP_FROM dans le fichier .env, puis redémarre le backend.",
                smtpHost,
                smtpFrom
            );
            return;
        }
        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            LOGGER.warn(
                "Configuration SMTP Keycloak partielle: host/from sont définis mais SPRING_MAIL_USERNAME/SPRING_MAIL_PASSWORD sont vides. "
                    + "Les e-mails Keycloak (execute-actions-email) échoueront tant que l'authentification SMTP n'est pas renseignée."
            );
        }

        boolean configured = keycloakAdminService.configureRealmSmtp(
            smtpHost,
            smtpPort,
            smtpFrom,
            smtpFromDisplayName,
            smtpUsername,
            smtpPassword,
            smtpStartTls,
            smtpSsl,
            smtpReplyTo
        );

        if (!configured) {
            LOGGER.warn(
                "SMTP Keycloak non synchronisé. Configurez-le manuellement via la console admin Keycloak si nécessaire."
            );
        }
    }
}
