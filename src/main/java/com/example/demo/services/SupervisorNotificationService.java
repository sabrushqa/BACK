package com.example.demo.services;

import com.example.demo.entities.commercant;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.notifications;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.TypeNotification;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Centralise les notifications (e-mail + in-app) liées au circuit d'assignation
 * des demandes d'auto-affiliation au superviseur puis au commercial.
 */
@Service
public class SupervisorNotificationService {

    private final UtilisateurRepository utilisateurRepository;
    private final NotificationsRepository notificationsRepository;
    private final AffiliationStatusMailService affiliationStatusMailService;

    public SupervisorNotificationService(
        UtilisateurRepository utilisateurRepository,
        NotificationsRepository notificationsRepository,
        AffiliationStatusMailService affiliationStatusMailService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.notificationsRepository = notificationsRepository;
        this.affiliationStatusMailService = affiliationStatusMailService;
    }

    public void notifyNewAutoAffiliationRequest(dossier_affiliation dossier, commercant commercant) {
        utilisateur supervisor = resolveSupervisor();
        if (supervisor == null) {
            return;
        }
        String subject = "Nouvelle demande d'auto-affiliation à assigner";
        String body = buildRequestSummary(dossier, commercant, null);
        affiliationStatusMailService.sendStatusUpdateEmail(supervisor.getEmail(), "Superviseur", subject, body);
        createNotification(
            supervisor,
            dossier.getIdDossier(),
            "Nouvelle demande d'auto-affiliation de " + displayName(commercant) + " à assigner.",
            TypeNotification.DOSSIER_A_ASSIGNER
        );
    }

    public void notifyUnassignedReminder(dossier_affiliation dossier, commercant commercant, long joursEnAttente) {
        utilisateur supervisor = resolveSupervisor();
        if (supervisor == null) {
            return;
        }
        String subject = "Rappel : demande d'auto-affiliation non assignée depuis %d jour(s)".formatted(joursEnAttente);
        String body = buildRequestSummary(dossier, commercant, joursEnAttente);
        affiliationStatusMailService.sendStatusUpdateEmail(supervisor.getEmail(), "Superviseur", subject, body);
        createNotification(
            supervisor,
            dossier.getIdDossier(),
            "Rappel : demande de " + displayName(commercant) + " toujours non assignée (" + joursEnAttente + " j).",
            TypeNotification.DOSSIER_A_ASSIGNER
        );
    }

    public void notifyUnprocessedAssignment(
        dossier_affiliation dossier,
        commercant commercant,
        commerciale commercialeAssignee,
        long joursDepuisAssignation
    ) {
        String subject = "Rappel : dossier assigné non traité depuis %d jour(s)".formatted(joursDepuisAssignation);
        String body =
            """
            Bonjour,

            Le dossier #%d (%s) assigné à %s n'a toujours pas été traité, %d jour(s) après son assignation.

            Merci de le finaliser rapidement ou de contacter le commercial concerné.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(
                dossier.getIdDossier(),
                displayName(commercant),
                displayName(commercialeAssignee),
                joursDepuisAssignation
            );

        if (commercialeAssignee != null && commercialeAssignee.getUtilisateur() != null) {
            affiliationStatusMailService.sendStatusUpdateEmail(
                commercialeAssignee.getUtilisateur().getEmail(),
                displayName(commercialeAssignee),
                subject,
                body
            );
        }

        utilisateur supervisor = resolveSupervisor();
        if (supervisor != null) {
            affiliationStatusMailService.sendStatusUpdateEmail(supervisor.getEmail(), "Superviseur", subject, body);
        }
    }

    public void notifyAssignedToCommercial(
        dossier_affiliation dossier,
        commercant commercant,
        commerciale commercialeAssignee
    ) {
        if (commercialeAssignee == null || commercialeAssignee.getUtilisateur() == null) {
            return;
        }

        String subject = "Nouvelle demande d'auto-affiliation assignée";
        String body =
            """
            Bonjour %s,

            Le superviseur vous a assigné une nouvelle demande d'auto-affiliation.

            Dossier : #%d
            Commerçant : %s

            Connectez-vous au portail pour la traiter.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(
                displayName(commercialeAssignee),
                dossier.getIdDossier(),
                displayName(commercant)
            );

        affiliationStatusMailService.sendStatusUpdateEmail(
            commercialeAssignee.getUtilisateur().getEmail(),
            displayName(commercialeAssignee),
            subject,
            body
        );

        createNotification(
            commercialeAssignee.getUtilisateur(),
            dossier.getIdDossier(),
            "Nouvelle demande d'auto-affiliation assignée : " + displayName(commercant) + ".",
            TypeNotification.DOSSIER_ASSIGNE
        );
    }

    /**
     * Notifie le commerçant (in-app + e-mail) dès qu'un TPE lui a été
     * réellement affecté — c'est ce moment précis qui débloque son espace
     * (voir MerchantAccessService::workspaceUnlocked).
     */
    public void notifyTpeAssigned(dossier_affiliation dossier, commercant commercant) {
        if (commercant == null || commercant.getUtilisateur() == null) {
            return;
        }
        utilisateur merchantUser = commercant.getUtilisateur();
        String subject = "Votre terminal de paiement a été affecté";
        String body =
            """
            Bonjour,

            Une référence TPE vient d'être affectée à votre dossier #%d.
            Votre espace commerçant Lana Cash est maintenant débloqué.

            Connectez-vous au portail pour y accéder.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(dossier.getIdDossier());

        affiliationStatusMailService.sendStatusUpdateEmail(
            merchantUser.getEmail(),
            displayName(commercant),
            subject,
            body
        );

        createNotification(
            merchantUser,
            dossier.getIdDossier(),
            "Un TPE a été affecté à votre dossier #" + dossier.getIdDossier() + " — votre espace est débloqué.",
            TypeNotification.TPE_AFFECTE
        );
    }

    private utilisateur resolveSupervisor() {
        return utilisateurRepository.findFirstByRole(RoleUser.SUPERVISEUR).orElse(null);
    }

    private void createNotification(
        utilisateur utilisateur,
        Long dossierId,
        String message,
        TypeNotification typeNotification
    ) {
        notifications notification = new notifications();
        notification.setUtilisateur(utilisateur);
        notification.setDossierId(dossierId);
        notification.setMessage(message);
        notification.setTypeNotification(typeNotification);
        notification.setDateEnvoi(LocalDate.now());
        notification.setStatutLecture(Boolean.FALSE);
        notificationsRepository.save(notification);
    }

    private String displayName(commercant commercant) {
        if (commercant == null) {
            return "un commerçant";
        }
        if (StringUtils.hasText(commercant.getNomCommercial())) {
            return commercant.getNomCommercial();
        }
        if (StringUtils.hasText(commercant.getRaisonSociale())) {
            return commercant.getRaisonSociale();
        }
        return "un commerçant";
    }

    private String displayName(commerciale commerciale) {
        if (commerciale == null) {
            return "un commercial";
        }
        String fullName = ((StringUtils.hasText(commerciale.getPrenom()) ? commerciale.getPrenom() + " " : "")
            + (commerciale.getNom() == null ? "" : commerciale.getNom())).trim();
        return StringUtils.hasText(fullName) ? fullName : "un commercial";
    }

    private String buildRequestSummary(dossier_affiliation dossier, commercant commercant, Long joursEnAttente) {
        String typeAffiliation = dossier.getTypeAffiliation() == null ? "-" : dossier.getTypeAffiliation().name();
        String natureJuridique =
            commercant == null || commercant.getType() == null ? "-" : commercant.getType().name();
        String secteur = commercant == null || !StringUtils.hasText(commercant.getSecteur())
            ? "-"
            : commercant.getSecteur();
        String ville = commercant == null || !StringUtils.hasText(commercant.getVille())
            ? "-"
            : commercant.getVille();

        String delaiLigne = joursEnAttente == null
            ? ""
            : "\nEn attente d'assignation depuis %d jour(s).\n".formatted(joursEnAttente);

        return """
            Bonjour,

            Une demande d'auto-affiliation est en attente d'assignation à un commercial.

            Dossier : #%d
            Nom du commerçant : %s
            Type d'affiliation : %s
            Nature juridique : %s
            Secteur d'activité : %s
            Ville : %s
            %s
            Merci de l'assigner à un commercial de la région concernée.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(
                dossier.getIdDossier(),
                displayName(commercant),
                typeAffiliation,
                natureJuridique,
                secteur,
                ville,
                delaiLigne
            );
    }
}
