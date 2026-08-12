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
        boolean read
    ) {
    }
}
