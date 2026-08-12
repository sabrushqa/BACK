package com.example.demo.controllers;

import com.example.demo.dto.NotificationOverviewResponse;
import com.example.demo.services.NotificationManagementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://localhost:*",
        "https://127.0.0.1:*"
    },
    allowedHeaders = {"Authorization", "Content-Type", "Accept"},
    allowCredentials = "true"
)
public class NotificationController {

    private final NotificationManagementService notificationManagementService;

    public NotificationController(
        NotificationManagementService notificationManagementService
    ) {
        this.notificationManagementService = notificationManagementService;
    }

    @GetMapping
    public ResponseEntity<NotificationOverviewResponse> getCurrentUserNotifications(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(
            notificationManagementService.getCurrentUserNotifications(authorizationHeader)
        );
    }

    @PostMapping("/read-all")
    public ResponseEntity<NotificationOverviewResponse> markAllAsRead(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(
            notificationManagementService.markAllAsRead(authorizationHeader)
        );
    }
}
