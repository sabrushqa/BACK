package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Verifie que les e-mails d'accuse de reception et de mise a jour de statut
 * degradent proprement (pas d'exception, sent=false) quand la config SMTP
 * est incomplete - un dossier ne doit jamais planter faute d'e-mail.
 */
class AffiliationMailServicesTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);

    @Test
    void submissionAcknowledgementDegradesGracefullyWhenSmtpUnavailable() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        AffiliationRequestMailService service = new AffiliationRequestMailService(
            mailSenderProvider, "sender@lanacash.ma", "password"
        );

        utilisateur user = new utilisateur();
        user.setEmail("nouveau.dossier@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Test Mail");

        AffiliationRequestMailService.MailDispatchResult result =
            service.sendSubmissionAcknowledgement(user, commercant);

        assertThat(result.sent()).isFalse();
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void statusUpdateEmailDegradesGracefullyWhenSmtpUnavailable() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        AffiliationStatusMailService service = new AffiliationStatusMailService(
            mailSenderProvider, "sender@lanacash.ma", "password"
        );

        boolean sent = service.sendStatusUpdateEmail(
            "destinataire@test.lanacash.ma", "Jane Doe", "Sujet", "Corps du message"
        );

        assertThat(sent).isFalse();
    }

    @Test
    void statusUpdateEmailReturnsFalseForBlankRecipient() {
        AffiliationStatusMailService service = new AffiliationStatusMailService(
            mailSenderProvider, "sender@lanacash.ma", "password"
        );

        assertThat(service.sendStatusUpdateEmail("  ", "Jane Doe", "Sujet", "Corps")).isFalse();
    }

    @Test
    void submissionAcknowledgementSendsEmailWhenSmtpIsAvailable() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        AffiliationRequestMailService service = new AffiliationRequestMailService(
            mailSenderProvider, "sender@lanacash.ma", "password"
        );

        utilisateur user = new utilisateur();
        user.setEmail("nouveau.dossier.envoye@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Test Mail Envoye");

        AffiliationRequestMailService.MailDispatchResult result =
            service.sendSubmissionAcknowledgement(user, commercant);

        assertThat(result.sent()).isTrue();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void submissionAcknowledgementDegradesGracefullyWhenSendThrows() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        AffiliationRequestMailService service = new AffiliationRequestMailService(
            mailSenderProvider, "sender@lanacash.ma", "password"
        );

        utilisateur user = new utilisateur();
        user.setEmail("dossier.echec.envoi@test.lanacash.ma");

        AffiliationRequestMailService.MailDispatchResult result =
            service.sendSubmissionAcknowledgement(user, null);

        assertThat(result.sent()).isFalse();
    }

    @Test
    void statusUpdateEmailSendsWithDefaultSubjectAndBodyWhenBlank() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        AffiliationStatusMailService service = new AffiliationStatusMailService(
            mailSenderProvider, "sender@lanacash.ma", "password"
        );

        boolean sent = service.sendStatusUpdateEmail(
            "destinataire.defaut@test.lanacash.ma", "Jane Doe", "  ", "  "
        );

        assertThat(sent).isTrue();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void statusUpdateEmailDegradesGracefullyWhenSendThrows() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        AffiliationStatusMailService service = new AffiliationStatusMailService(
            mailSenderProvider, "sender@lanacash.ma", "password"
        );

        boolean sent = service.sendStatusUpdateEmail(
            "destinataire.echec@test.lanacash.ma", "Jane Doe", "Sujet", "Corps"
        );

        assertThat(sent).isFalse();
    }
}
