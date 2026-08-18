package com.example.demo.dto;

import java.time.LocalDate;
import java.util.List;

public record NotificationOverviewResponse(
    long unreadCount,
    List<NotificationItem> notifications
) {

    public record NotificationItem(
        Long notificationId,
        Long dossierId,
        String message,
        String type,
        LocalDate dateEnvoi,
        boolean read,
        // Distingue un dossier d'extension (NOUVEAU_PDV) du dossier
        // d'affiliation initial : le front en a besoin pour construire le
        // bon lien (BackofficeDemandeExtentionDetailPage vs
        // BackofficeDossierDetailPage) au clic sur la notification.
        boolean isNewPdvRequest
    ) {
    }
}
