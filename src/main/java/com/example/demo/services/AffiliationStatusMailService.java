package com.example.demo.services;

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
public class AffiliationStatusMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        AffiliationStatusMailService.class
    );

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String senderAddress;
    private final String senderPassword;

    public AffiliationStatusMailService(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        @Value("${spring.mail.username:}") String senderAddress,
        @Value("${spring.mail.password:}") String senderPassword
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.senderAddress = senderAddress;
        this.senderPassword = senderPassword;
    }

    public boolean sendStatusUpdateEmail(
        String recipientEmail,
        String recipientName,
        String subject,
        String body
    ) {
        if (!StringUtils.hasText(recipientEmail)) {
            return false;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || !StringUtils.hasText(senderAddress) || !StringUtils.hasText(senderPassword)) {
            LOGGER.warn("Status update e-mail skipped because SMTP configuration is incomplete.");
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(recipientEmail.trim());
        message.setSubject(
            StringUtils.hasText(subject)
                ? subject.trim()
                : "Mise à jour de votre dossier Lana Cash"
        );
        message.setText(
            StringUtils.hasText(body)
                ? body.trim()
                : """
                Bonjour,

                Une mise à jour est disponible concernant votre dossier.

                Cordialement,
                L'équipe Lana Cash
                """
        );

        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            LOGGER.error(
                "Unable to send status update e-mail to {} ({})",
                recipientEmail,
                recipientName,
                exception
            );
            return false;
        }
    }
}
