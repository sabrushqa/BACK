package com.example.demo.services;

import com.example.demo.dto.NotificationOverviewResponse;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.notifications;
import com.example.demo.entities.utilisateur;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class NotificationManagementService {

    private static final int NOTIFICATION_RETENTION_DAYS = 30;

    private final NotificationsRepository notificationsRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final JwtService jwtService;

    public NotificationManagementService(
        NotificationsRepository notificationsRepository,
        UtilisateurRepository utilisateurRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        JwtService jwtService
    ) {
        this.notificationsRepository = notificationsRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public NotificationOverviewResponse getCurrentUserNotifications(String authorizationHeader) {
        utilisateur utilisateur = readAuthenticatedUser(authorizationHeader);
        LocalDate since = LocalDate.now().minusDays(NOTIFICATION_RETENTION_DAYS);
        List<notifications> rawItems = notificationsRepository
            .findTop20ByUtilisateur_IdAndDateEnvoiGreaterThanEqualOrderByDateEnvoiDescIdNotificationDesc(
                utilisateur.getId(),
                since
            );
        List<NotificationOverviewResponse.NotificationItem> items = mapNotificationItems(rawItems);

        return new NotificationOverviewResponse(
            notificationsRepository.countByUtilisateur_IdAndStatutLectureFalseAndDateEnvoiGreaterThanEqual(
                utilisateur.getId(),
                since
            ),
            items
        );
    }

    public NotificationOverviewResponse markAllAsRead(String authorizationHeader) {
        utilisateur utilisateur = readAuthenticatedUser(authorizationHeader);
        LocalDate since = LocalDate.now().minusDays(NOTIFICATION_RETENTION_DAYS);
        List<notifications> items = notificationsRepository
            .findTop20ByUtilisateur_IdAndDateEnvoiGreaterThanEqualOrderByDateEnvoiDescIdNotificationDesc(
                utilisateur.getId(),
                since
            );

        for (notifications item : items) {
            if (!Boolean.TRUE.equals(item.getStatutLecture())) {
                item.setStatutLecture(Boolean.TRUE);
            }
        }

        notificationsRepository.saveAll(items);

        return new NotificationOverviewResponse(
            0,
            mapNotificationItems(items)
        );
    }

    /**
     * Un seul aller-retour DB pour resoudre l'origine (NOUVEAU_PDV ou non) de
     * tous les dossiers references par ce lot de notifications, plutot qu'une
     * requete par notification (jusqu'a 20 ici) — le front en a besoin pour
     * lier vers la bonne page (BackofficeDemandeExtentionDetailPage vs
     * BackofficeDossierDetailPage) au clic.
     */
    private List<NotificationOverviewResponse.NotificationItem> mapNotificationItems(List<notifications> items) {
        List<Long> dossierIds = items.stream()
            .map(notifications::getDossierId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, Boolean> isNewPdvRequestByDossierId = dossierAffiliationRepository
            .findAllById(dossierIds)
            .stream()
            .collect(Collectors.toMap(
                dossier_affiliation::getIdDossier,
                dossier -> "NOUVEAU_PDV".equalsIgnoreCase(
                    Objects.requireNonNullElse(dossier.getOrigineCreation(), "").trim()
                )
            ));

        return items.stream()
            .map(item -> mapNotificationItem(
                item,
                Boolean.TRUE.equals(isNewPdvRequestByDossierId.get(item.getDossierId()))
            ))
            .toList();
    }

    private NotificationOverviewResponse.NotificationItem mapNotificationItem(
        notifications item,
        boolean isNewPdvRequest
    ) {
        return new NotificationOverviewResponse.NotificationItem(
            item.getIdNotification(),
            item.getDossierId(),
            Objects.requireNonNullElse(item.getMessage(), ""),
            item.getTypeNotification() == null ? "" : item.getTypeNotification().name(),
            item.getDateEnvoi(),
            Boolean.TRUE.equals(item.getStatutLecture()),
            isNewPdvRequest
        );
    }

    private utilisateur readAuthenticatedUser(String authorizationHeader) {
        String token = jwtService.extractBearerToken(authorizationHeader)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentification JWT requise."
                )
            );

        if (jwtService.isTokenExpired(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT expire.");
        }

        Long utilisateurId = jwtService.extractUserId(token);
        if (utilisateurId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT invalide.");
        }

        utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session introuvable."));

        if (jwtService.isSessionInvalidated(token, utilisateur)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session JWT invalidee.");
        }

        return utilisateur;
    }
}
