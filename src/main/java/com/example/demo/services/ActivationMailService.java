package com.example.demo.services;

import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ActivationMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivationMailService.class);
    private static final DateTimeFormatter EXPIRATION_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String senderAddress;
    private final String senderPassword;
    private final String frontendBaseUrl;

    public ActivationMailService(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        @Value("${spring.mail.username:}") String senderAddress,
        @Value("${spring.mail.password:}") String senderPassword,
        @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.senderAddress = senderAddress;
        this.senderPassword = senderPassword;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
    }

    public MailDispatchResult sendActivationEmail(
        utilisateur utilisateur,
        commercant commercant,
        String temporaryPassword
    ) {
        String displayName =
            commercant != null && StringUtils.hasText(commercant.getNomCommercial())
                ? commercant.getNomCommercial()
                : commercant != null && StringUtils.hasText(commercant.getRaisonSociale())
                    ? commercant.getRaisonSociale()
                    : Objects.requireNonNullElse(utilisateur.getEmail(), "client");

        String introduction = commercant == null
            ? "Votre accès superviseur Lana Cash a été préparé avec succès."
            : "Votre demande d'affiliation a bien été enregistrée et votre espace Lana Cash est pret a etre activé.";

        return sendActivationEmail(utilisateur, temporaryPassword, displayName, introduction);
    }

    public MailDispatchResult sendAccountSetupEmail(
        utilisateur utilisateur,
        String recipientName,
        String accessLabel,
        String temporaryPassword
    ) {
        String displayName = StringUtils.hasText(recipientName)
            ? recipientName.trim()
            : Objects.requireNonNullElse(utilisateur.getEmail(), "client");
        String introduction =
            "Votre accès "
                + (StringUtils.hasText(accessLabel) ? accessLabel.trim() : "professionnel")
                + " Lana Cash a été préparé avec succès.";

        return sendActivationEmail(utilisateur, temporaryPassword, displayName, introduction);
    }

    private MailDispatchResult sendActivationEmail(
        utilisateur utilisateur,
        String temporaryPassword,
        String displayName,
        String introduction
    ) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || !StringUtils.hasText(senderAddress) || !StringUtils.hasText(senderPassword)) {
            LOGGER.warn("Activation e-mail skipped because SMTP configuration is incomplete.");
            return new MailDispatchResult(
                false,
                "Le dossier est enregistré, mais l'e-mail d'activation n'a pas pu etre envoyé automatiquement."
            );
        }

        String activationLink =
            frontendBaseUrl
                + "/activate-account?email="
                + encode(utilisateur.getEmail());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(utilisateur.getEmail());
        message.setSubject("Activation de votre compte Lana Cash");
        message.setText(
            """
            Bonjour %s,

            %s

            Pour finaliser l'activation de votre compte, veuillez utiliser le mot de passe temporaire suivant :
            %s

            Identifiant a utiliser sur le portail Lana Cash :
            %s

            Ce mot de passe temporaire expire le %s.

            Cliquez sur le lien suivant pour activer votre compte dans le portail Lana Cash. Utilisez ce lien, pas la console Keycloak : le portail mettra ensuite votre mot de passe à jour dans Keycloak et finalisera l'activation.
            %s

            Si vous n’êtes pas à l’origine de cette demande, nous vous invitons à ignorer cet e-mail.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(
                displayName,
                introduction,
                temporaryPassword,
                utilisateur.getEmail(),
                utilisateur.getPasswordExpiresAt() == null
                    ? "bientot"
                    : utilisateur.getPasswordExpiresAt().format(EXPIRATION_FORMAT),
                activationLink
            )
        );

        try {
            mailSender.send(message);
            return new MailDispatchResult(
                true,
                "Un e-mail d'activation contenant un mot de passe temporaire a été envoyé."
            );
        } catch (MailException exception) {
            LOGGER.error("Unable to send activation e-mail to {}", utilisateur.getEmail(), exception);
            return new MailDispatchResult(
                false,
                "Le dossier est enregistré, mais l'e-mail d'activation n'a pas pu etre envoyé automatiquement."
            );
        }
    }

    public record MailDispatchResult(boolean sent, String message) {
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
