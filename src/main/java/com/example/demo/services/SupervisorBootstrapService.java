package com.example.demo.services;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SupervisorBootstrapService implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisorBootstrapService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordHashService passwordHashService;
    private final ActivationMailService activationMailService;
    private final KeycloakAdminService keycloakAdminService;
    private final boolean bootstrapEnabled;
    private final String supervisorEmail;
    private final long activationExpirationMinutes;

    public SupervisorBootstrapService(
        UtilisateurRepository utilisateurRepository,
        PasswordHashService passwordHashService,
        ActivationMailService activationMailService,
        KeycloakAdminService keycloakAdminService,
        @Value("${app.supervisor.bootstrap.enabled:true}") boolean bootstrapEnabled,
        @Value("${app.supervisor.bootstrap.e-mail:Superviseur_portail@lanacash.ma}") String supervisorEmail,
        @Value("${app.auth.activation-expiration-minutes:60}") long activationExpirationMinutes
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordHashService = passwordHashService;
        this.activationMailService = activationMailService;
        this.keycloakAdminService = keycloakAdminService;
        this.bootstrapEnabled = bootstrapEnabled;
        this.supervisorEmail = supervisorEmail;
        this.activationExpirationMinutes = activationExpirationMinutes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled || !StringUtils.hasText(supervisorEmail)) {
            return;
        }

        String normalizedEmail = supervisorEmail.trim().toLowerCase(Locale.ROOT);
        utilisateur utilisateur = utilisateurRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (utilisateur == null) {
            provisionNewSupervisor(normalizedEmail);
            return;
        }

        if (utilisateur.getRole() != RoleUser.SUPERVISEUR) {
            LOGGER.warn(
                "Supervisor bootstrap skipped because {} already exists with role {}.",
                normalizedEmail,
                utilisateur.getRole()
            );
            return;
        }

        if (Boolean.TRUE.equals(utilisateur.getActive())) {
            LOGGER.info("Supervisor bootstrap skipped because {} is already activé.", normalizedEmail);
            return;
        }

        reissueTemporaryPassword(utilisateur);
    }

    private void provisionNewSupervisor(String normalizedEmail) {
        utilisateur utilisateur = new utilisateur();
        utilisateur.setEmail(normalizedEmail);
        utilisateur.setRole(RoleUser.SUPERVISEUR);
        utilisateur.setActive(Boolean.FALSE);
        utilisateur.setTokenVersion(0);
        reissueTemporaryPassword(utilisateur);
    }

    private void reissueTemporaryPassword(utilisateur utilisateur) {
        String temporaryPassword = generateTemporaryPassword();

        utilisateur.setPassword(null);
        utilisateur.setActive(Boolean.FALSE);
        utilisateur.setDateActivation(null);
        utilisateur.setPasswordExpiresAt(
            LocalDateTime.now().plusMinutes(activationExpirationMinutes)
        );
        utilisateur.setTokenVersion(resolveTokenVersion(utilisateur) + 1);
        utilisateur.setLoginOtpChallengeId(null);
        utilisateur.setLoginOtpCodeHash(null);
        utilisateur.setLoginOtpExpiresAt(null);
        utilisateur.setLoginOtpFailedAttempts(null);
        utilisateur.setPasswordResetCodeHash(null);
        utilisateur.setPasswordResetExpiresAt(null);
        utilisateur.setPasswordResetFailedAttempts(null);

        utilisateur = utilisateurRepository.save(utilisateur);
        if (!keycloakAdminService.provisionUser(utilisateur, temporaryPassword)) {
            LOGGER.warn(
                "Supervisor account prepared for {}, but Keycloak provisioning failed. Activation e-mail skipped.",
                utilisateur.getEmail()
            );
            return;
        }
        utilisateur = utilisateurRepository.save(utilisateur);

        ActivationMailService.MailDispatchResult dispatchResult =
            activationMailService.sendActivationEmail(utilisateur, null, temporaryPassword);

        if (dispatchResult.sent()) {
            LOGGER.info("Supervisor activation e-mail sent to {}.", utilisateur.getEmail());
        } else {
            LOGGER.warn(
                "Supervisor account prepared for {}, but activation e-mail could not be sent automatically.",
                utilisateur.getEmail()
            );
        }
    }

    private String generateTemporaryPassword() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }

    private int resolveTokenVersion(utilisateur utilisateur) {
        return utilisateur.getTokenVersion() == null ? 0 : utilisateur.getTokenVersion();
    }
}
