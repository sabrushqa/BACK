package com.example.demo.services;

import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
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
public class AffiliationRequestMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AffiliationRequestMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String senderAddress;
    private final String senderPassword;

    public AffiliationRequestMailService(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        @Value("${spring.mail.username:}") String senderAddress,
        @Value("${spring.mail.password:}") String senderPassword
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.senderAddress = senderAddress;
        this.senderPassword = senderPassword;
    }

    public MailDispatchResult sendSubmissionAcknowledgement(
        utilisateur utilisateur,
        commercant commercant
    ) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || !StringUtils.hasText(senderAddress) || !StringUtils.hasText(senderPassword)) {
            LOGGER.warn("Affiliation acknowledgement e-mail skipped because SMTP configuration is incomplete.");
            return new MailDispatchResult(
                false,
                "Votre demande est bien enregistrée. Un commercial Lana Cash vous contactera pour finaliser le dossier."
            );
        }

        String displayName =
            commercant != null && StringUtils.hasText(commercant.getNomCommercial())
                ? commercant.getNomCommercial()
                : commercant != null && StringUtils.hasText(commercant.getRaisonSociale())
                    ? commercant.getRaisonSociale()
                    : Objects.requireNonNullElse(utilisateur.getEmail(), "client");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(utilisateur.getEmail());
        message.setSubject("Confirmation de reception de votre demande d'affiliation Lana Cash");
        message.setText(
            """
            Bonjour %s,

            Nous vous confirmons la bonne reception de votre demande d'affiliation Lana Cash.

            Votre dossier est actuellement en cours d'analyse par notre équipe commerciale. Un commercial vous contactera prochainement afin de compléter les informations necessaires et de finaliser votre dossier.

            Une fois cette étape finalisee, vous recevrez un e-mail d'activation contenant vos informations d'accès.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(displayName)
        );

        try {
            mailSender.send(message);
            return new MailDispatchResult(
                true,
                "Un e-mail de confirmation a été envoyé. Un commercial Lana Cash vous contactera prochainement."
            );
        } catch (MailException exception) {
            LOGGER.error(
                "Unable to send affiliation acknowledgement e-mail to {}",
                utilisateur.getEmail(),
                exception
            );
            return new MailDispatchResult(
                false,
                "Votre demande est bien enregistrée. Un commercial Lana Cash vous contactera pour finaliser le dossier."
            );
        }
    }

    public record MailDispatchResult(boolean sent, String message) {
    }
}
