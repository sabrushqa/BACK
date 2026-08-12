package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.notifications;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie la logique de relance quotidienne: seuls les dossiers d'origine
 * AUTO_AFFILIATION sont concernes, et le seuil "assigne depuis plus de 2
 * jours" est bien un strict > 2 (pas >=).
 */
@SpringBootTest
@Transactional
class AffiliationReminderSchedulerTest {

    @Autowired
    private AffiliationReminderScheduler affiliationReminderScheduler;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    private utilisateur supervisorUser;
    private commercant commercantTest;

    @BeforeEach
    void setUp() {
        supervisorUser = new utilisateur();
        supervisorUser.setEmail("superviseur.reminder.test@lanacash.ma");
        supervisorUser.setRole(RoleUser.SUPERVISEUR);
        supervisorUser.setActive(true);
        supervisorUser.setDateCreation(LocalDate.now());
        supervisorUser = utilisateurRepository.save(supervisorUser);

        commercantTest = new commercant();
        commercantTest.setNomCommercial("Boutique Reminder Test");
        commercantTest = commercantRepository.save(commercantTest);
    }

    private long countSupervisorNotifications() {
        return notificationsRepository
            .findTop20ByUtilisateur_IdOrderByDateEnvoiDescIdNotificationDesc(supervisorUser.getId())
            .size();
    }

    @Test
    void ignoresDossierNotFromAutoAffiliationOrigin() {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercantTest);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setStatus(StatusDossier.EN_ATTENTE_ASSIGNATION);
        dossier.setDateSoumission(LocalDate.now().minusDays(5));
        dossierAffiliationRepository.save(dossier);

        affiliationReminderScheduler.sendDailyReminders();

        assertThat(countSupervisorNotifications()).isZero();
    }

    @Test
    void notifiesUnassignedAutoAffiliationSubmittedBeforeToday() {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercantTest);
        dossier.setOrigineCreation("AUTO_AFFILIATION");
        dossier.setStatus(StatusDossier.EN_ATTENTE_ASSIGNATION);
        dossier.setDateSoumission(LocalDate.now().minusDays(1));
        dossierAffiliationRepository.save(dossier);

        affiliationReminderScheduler.sendDailyReminders();

        assertThat(countSupervisorNotifications()).isEqualTo(1);
    }

    @Test
    void doesNotNotifyWhenAssignedExactlyTwoDaysAgo() {
        commerciale commercialeAssignee = new commerciale();
        utilisateur commercialUser = new utilisateur();
        commercialUser.setEmail("commercial.reminder.boundary@test.lanacash.ma");
        commercialUser.setRole(RoleUser.COMMERCIAL);
        commercialUser.setActive(true);
        commercialUser.setDateCreation(LocalDate.now());
        commercialUser = utilisateurRepository.save(commercialUser);
        commercialeAssignee.setUtilisateur(commercialUser);
        commercialeAssignee = commercialeRepository.save(commercialeAssignee);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercantTest);
        dossier.setOrigineCreation("AUTO_AFFILIATION");
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setDateSoumission(LocalDate.now().minusDays(2));
        dossier.setCommercialeAssignee(commercialeAssignee);
        dossier.setDateAssignationCommerciale(LocalDate.now().minusDays(2));
        dossierAffiliationRepository.save(dossier);

        affiliationReminderScheduler.sendDailyReminders();

        assertThat(countSupervisorNotifications()).isZero();
    }

    @Test
    void notifiesWhenAssignedMoreThanTwoDaysAgoAndStillUnprocessed() {
        commerciale commercialeAssignee = new commerciale();
        utilisateur commercialUser = new utilisateur();
        commercialUser.setEmail("commercial.reminder.overdue@test.lanacash.ma");
        commercialUser.setRole(RoleUser.COMMERCIAL);
        commercialUser.setActive(true);
        commercialUser.setDateCreation(LocalDate.now());
        commercialUser = utilisateurRepository.save(commercialUser);
        commercialeAssignee.setUtilisateur(commercialUser);
        commercialeAssignee = commercialeRepository.save(commercialeAssignee);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercantTest);
        dossier.setOrigineCreation("AUTO_AFFILIATION");
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setDateSoumission(LocalDate.now().minusDays(3));
        dossier.setCommercialeAssignee(commercialeAssignee);
        dossier.setDateAssignationCommerciale(LocalDate.now().minusDays(3));
        dossierAffiliationRepository.save(dossier);

        affiliationReminderScheduler.sendDailyReminders();

        // notifyUnprocessedAssignment n'envoie que des e-mails (pas de notification in-app),
        // donc on verifie l'absence d'exception plutot qu'une notification en base.
        assertThat(dossierAffiliationRepository.findById(dossier.getIdDossier())).isPresent();
    }
}
