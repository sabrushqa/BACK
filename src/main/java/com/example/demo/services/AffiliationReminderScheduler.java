package com.example.demo.services;

import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.enums.StatusDossier;
import com.example.demo.repositories.DossierAffiliationRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relances quotidiennes pour les demandes d'auto-affiliation :
 * - non assignees depuis au moins 1 jour (superviseur relance chaque jour) ;
 * - assignees depuis plus de 2 jours et toujours non traitees (commercial + superviseur).
 */
@Service
@Transactional
public class AffiliationReminderScheduler {

    private static final String ORIGINE_AUTO_AFFILIATION = "AUTO_AFFILIATION";

    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final SupervisorNotificationService supervisorNotificationService;

    public AffiliationReminderScheduler(
        DossierAffiliationRepository dossierAffiliationRepository,
        SupervisorNotificationService supervisorNotificationService
    ) {
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.supervisorNotificationService = supervisorNotificationService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyReminders() {
        LocalDate today = LocalDate.now();

        for (dossier_affiliation dossier : dossierAffiliationRepository.findAll()) {
            if (!ORIGINE_AUTO_AFFILIATION.equalsIgnoreCase(dossier.getOrigineCreation())) {
                continue;
            }

            commercant commercant = dossier.getCommercant();

            if (
                dossier.getStatus() == StatusDossier.EN_ATTENTE_ASSIGNATION
                    && dossier.getDateSoumission() != null
                    && dossier.getDateSoumission().isBefore(today)
            ) {
                long joursEnAttente = ChronoUnit.DAYS.between(dossier.getDateSoumission(), today);
                supervisorNotificationService.notifyUnassignedReminder(dossier, commercant, joursEnAttente);
                continue;
            }

            if (
                dossier.getStatus() == StatusDossier.SOUMIS
                    && dossier.getCommercialeAssignee() != null
                    && dossier.getDateAssignationCommerciale() != null
            ) {
                long joursDepuisAssignation = ChronoUnit.DAYS.between(
                    dossier.getDateAssignationCommerciale(),
                    today
                );
                if (joursDepuisAssignation > 2) {
                    supervisorNotificationService.notifyUnprocessedAssignment(
                        dossier,
                        commercant,
                        dossier.getCommercialeAssignee(),
                        joursDepuisAssignation
                    );
                }
            }
        }
    }
}
