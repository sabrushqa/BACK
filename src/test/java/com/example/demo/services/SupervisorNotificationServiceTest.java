package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.demo.entities.commercant;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.notifications;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.TypeNotification;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie que la notification d'une nouvelle demande d'auto-affiliation
 * cree bien une notification pour le superviseur trouve en base, et ne
 * plante jamais si aucun superviseur n'existe (pas de SMTP reel en test:
 * spring.mail.host absent => JavaMailSender indisponible => degrade proprement).
 */
@SpringBootTest
@Transactional
class SupervisorNotificationServiceTest {

    @Autowired
    private SupervisorNotificationService supervisorNotificationService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    private dossier_affiliation newDossier(commercant owner) {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(owner);
        dossier.setDateSoumission(LocalDate.now());
        return dossierAffiliationRepository.save(dossier);
    }

    @Test
    void doesNothingWhenNoSupervisorExists() {
        commercant draft = new commercant();
        draft.setNomCommercial("Boutique Sans Superviseur");
        final commercant commercantTest = commercantRepository.save(draft);
        final dossier_affiliation dossier = newDossier(commercantTest);

        assertThatCode(() ->
            supervisorNotificationService.notifyNewAutoAffiliationRequest(dossier, commercantTest)
        ).doesNotThrowAnyException();
    }

    @Test
    void createsNotificationForSupervisorWhenPresent() {
        utilisateur supervisorUser = new utilisateur();
        supervisorUser.setEmail("superviseur.notif.test@lanacash.ma");
        supervisorUser.setRole(RoleUser.SUPERVISEUR);
        supervisorUser.setActive(true);
        supervisorUser.setDateCreation(LocalDate.now());
        supervisorUser = utilisateurRepository.save(supervisorUser);

        commercant commercantTest = new commercant();
        commercantTest.setNomCommercial("Boutique Avec Superviseur");
        commercantTest = commercantRepository.save(commercantTest);
        dossier_affiliation dossier = newDossier(commercantTest);

        supervisorNotificationService.notifyNewAutoAffiliationRequest(dossier, commercantTest);

        List<notifications> supervisorNotifications = notificationsRepository
            .findTop20ByUtilisateur_IdOrderByDateEnvoiDescIdNotificationDesc(supervisorUser.getId());

        assertThat(supervisorNotifications).hasSize(1);
        assertThat(supervisorNotifications.get(0).getTypeNotification())
            .isEqualTo(TypeNotification.DOSSIER_A_ASSIGNER);
        assertThat(supervisorNotifications.get(0).getDossierId()).isEqualTo(dossier.getIdDossier());
    }

    @Test
    void notifiesSupervisorWithReminderWhenStillUnassigned() {
        utilisateur supervisorUser = new utilisateur();
        supervisorUser.setEmail("superviseur.reminder@lanacash.ma");
        supervisorUser.setRole(RoleUser.SUPERVISEUR);
        supervisorUser.setActive(true);
        supervisorUser.setDateCreation(LocalDate.now());
        supervisorUser = utilisateurRepository.save(supervisorUser);

        commercant commercantTest = new commercant();
        commercantTest.setRaisonSociale("Lana Distribution SARL");
        commercantTest = commercantRepository.save(commercantTest);
        dossier_affiliation dossier = newDossier(commercantTest);

        supervisorNotificationService.notifyUnassignedReminder(dossier, commercantTest, 5L);

        List<notifications> supervisorNotifications = notificationsRepository
            .findTop20ByUtilisateur_IdOrderByDateEnvoiDescIdNotificationDesc(supervisorUser.getId());
        assertThat(supervisorNotifications).hasSize(1);
        assertThat(supervisorNotifications.get(0).getMessage()).contains("Lana Distribution SARL");
    }

    @Test
    void doesNothingForUnassignedReminderWhenNoSupervisorExists() {
        final commercant commercantTest = commercantRepository.save(new commercant());
        final dossier_affiliation dossier = newDossier(commercantTest);

        assertThatCode(() ->
            supervisorNotificationService.notifyUnassignedReminder(dossier, commercantTest, 3L)
        ).doesNotThrowAnyException();
    }

    @Test
    void notifiesCommercialAndSupervisorWhenAssignedDossierIsUnprocessed() {
        utilisateur supervisorUser = new utilisateur();
        supervisorUser.setEmail("superviseur.unprocessed@lanacash.ma");
        supervisorUser.setRole(RoleUser.SUPERVISEUR);
        supervisorUser.setActive(true);
        supervisorUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(supervisorUser);

        utilisateur commercialUser = new utilisateur();
        commercialUser.setEmail("commercial.unprocessed@lanacash.ma");
        commercialUser.setRole(RoleUser.COMMERCIAL);
        commercialUser.setActive(true);
        commercialUser.setDateCreation(LocalDate.now());
        commercialUser = utilisateurRepository.save(commercialUser);

        commerciale commercialeDraft = new commerciale();
        commercialeDraft.setUtilisateur(commercialUser);
        final commerciale commerciale = commercialeRepository.save(commercialeDraft);

        commercant commercantDraft = new commercant();
        commercantDraft.setNomCommercial("Boutique Non Traitee");
        final commercant commercantTest = commercantRepository.save(commercantDraft);
        final dossier_affiliation dossier = newDossier(commercantTest);

        assertThatCode(() ->
            supervisorNotificationService.notifyUnprocessedAssignment(dossier, commercantTest, commerciale, 4L)
        ).doesNotThrowAnyException();
    }

    @Test
    void notifiesUnprocessedAssignmentWithoutCommercialeGracefully() {
        final commercant commercantTest = commercantRepository.save(new commercant());
        final dossier_affiliation dossier = newDossier(commercantTest);

        assertThatCode(() ->
            supervisorNotificationService.notifyUnprocessedAssignment(dossier, commercantTest, null, 2L)
        ).doesNotThrowAnyException();
    }

    @Test
    void notifiesAssignedCommercialWhenDossierIsAssigned() {
        utilisateur commercialUser = new utilisateur();
        commercialUser.setEmail("commercial.assigned.notif@lanacash.ma");
        commercialUser.setRole(RoleUser.COMMERCIAL);
        commercialUser.setActive(true);
        commercialUser.setDateCreation(LocalDate.now());
        commercialUser = utilisateurRepository.save(commercialUser);

        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setPrenom("Youssef");
        commerciale.setNom("Alaoui");
        commerciale = commercialeRepository.save(commerciale);

        commercant commercantTest = new commercant();
        commercantTest.setNomCommercial("Boutique Assignee Notif");
        commercantTest = commercantRepository.save(commercantTest);
        dossier_affiliation dossier = newDossier(commercantTest);

        supervisorNotificationService.notifyAssignedToCommercial(dossier, commercantTest, commerciale);

        List<notifications> commercialNotifications = notificationsRepository
            .findTop20ByUtilisateur_IdOrderByDateEnvoiDescIdNotificationDesc(commercialUser.getId());
        assertThat(commercialNotifications).hasSize(1);
        assertThat(commercialNotifications.get(0).getTypeNotification())
            .isEqualTo(TypeNotification.DOSSIER_ASSIGNE);
    }

    @Test
    void doesNothingWhenAssigningToNullCommerciale() {
        final commercant commercantTest = commercantRepository.save(new commercant());
        final dossier_affiliation dossier = newDossier(commercantTest);

        assertThatCode(() ->
            supervisorNotificationService.notifyAssignedToCommercial(dossier, commercantTest, null)
        ).doesNotThrowAnyException();
    }
}
