package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Verifie que l'envoi d'e-mail d'activation degrade proprement (pas
 * d'exception, resultat "sent=false") quand la configuration SMTP est
 * incomplete - un dossier ne doit jamais planter faute d'e-mail.
 */
class ActivationMailServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);

    @Test
    void gracefullyDegradesWhenMailSenderIsUnavailable() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        ActivationMailService activationMailService = new ActivationMailService(
            mailSenderProvider,
            "sender@lanacash.ma",
            "password",
            "http://localhost:4200"
        );

        utilisateur user = new utilisateur();
        user.setEmail("nouveau.commercant@test.lanacash.ma");

        ActivationMailService.MailDispatchResult result = activationMailService.sendAccountSetupEmail(
            user,
            "Jane Doe",
            "commerçant",
            "TempPass123!"
        );

        assertThat(result.sent()).isFalse();
    }

    @Test
    void gracefullyDegradesWhenSmtpCredentialsAreMissing() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        ActivationMailService activationMailService = new ActivationMailService(
            mailSenderProvider,
            "",
            "",
            "http://localhost:4200"
        );

        utilisateur user = new utilisateur();
        user.setEmail("nouveau.commercant2@test.lanacash.ma");

        ActivationMailService.MailDispatchResult result = activationMailService.sendAccountSetupEmail(
            user,
            "Jane Doe",
            "commerçant",
            "TempPass123!"
        );

        assertThat(result.sent()).isFalse();
    }

    @Test
    void sendsAccountSetupEmailSuccessfullyWithLabelAndExpiration() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        ActivationMailService activationMailService = new ActivationMailService(
            mailSenderProvider, "sender@lanacash.ma", "password", "http://localhost:4200/"
        );

        utilisateur user = new utilisateur();
        user.setEmail("compte.setup@test.lanacash.ma");
        user.setPasswordExpiresAt(LocalDateTime.now().plusHours(2));

        ActivationMailService.MailDispatchResult result = activationMailService.sendAccountSetupEmail(
            user, "Jane Doe", "back office", "TempPass123!"
        );

        assertThat(result.sent()).isTrue();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsAccountSetupEmailUsingEmailAsDisplayNameWhenRecipientNameBlank() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        ActivationMailService activationMailService = new ActivationMailService(
            mailSenderProvider, "sender@lanacash.ma", "password", "http://localhost:4200"
        );

        utilisateur user = new utilisateur();
        user.setEmail("sansnom.setup@test.lanacash.ma");

        ActivationMailService.MailDispatchResult result = activationMailService.sendAccountSetupEmail(
            user, "  ", "", "TempPass123!"
        );

        assertThat(result.sent()).isTrue();
    }

    @Test
    void sendsActivationEmailForMerchantWithNomCommercial() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        ActivationMailService activationMailService = new ActivationMailService(
            mailSenderProvider, "sender@lanacash.ma", "password", "http://localhost:4200"
        );

        utilisateur user = new utilisateur();
        user.setEmail("activation.commercant@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Activation Test");

        ActivationMailService.MailDispatchResult result =
            activationMailService.sendActivationEmail(user, commercant, "TempPass123!");

        assertThat(result.sent()).isTrue();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsActivationEmailForMerchantWithRaisonSocialeWhenNomCommercialBlank() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        ActivationMailService activationMailService = new ActivationMailService(
            mailSenderProvider, "sender@lanacash.ma", "password", "http://localhost:4200"
        );

        utilisateur user = new utilisateur();
        user.setEmail("activation.raisonsociale@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setRaisonSociale("Lana Distribution SARL");

        ActivationMailService.MailDispatchResult result =
            activationMailService.sendActivationEmail(user, commercant, "TempPass123!");

        assertThat(result.sent()).isTrue();
    }

    @Test
    void sendsActivationEmailForSupervisorWhenMerchantIsNull() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        ActivationMailService activationMailService = new ActivationMailService(
            mailSenderProvider, "sender@lanacash.ma", "password", "http://localhost:4200"
        );

        utilisateur user = new utilisateur();
        user.setEmail("activation.superviseur@test.lanacash.ma");

        ActivationMailService.MailDispatchResult result =
            activationMailService.sendActivationEmail(user, null, "TempPass123!");

        assertThat(result.sent()).isTrue();
    }

    @Test
    void degradesGracefullyWhenSendThrowsMailException() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        ActivationMailService activationMailService = new ActivationMailService(
            mailSenderProvider, "sender@lanacash.ma", "password", "http://localhost:4200"
        );

        utilisateur user = new utilisateur();
        user.setEmail("activation.echec@test.lanacash.ma");

        ActivationMailService.MailDispatchResult result =
            activationMailService.sendActivationEmail(user, null, "TempPass123!");

        assertThat(result.sent()).isFalse();
    }
}
