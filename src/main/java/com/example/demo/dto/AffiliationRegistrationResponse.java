package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AffiliationRegistrationResponse {

    private final Long utilisateurId;
    private final Long commercantId;
    private final Long dossierId;
    private final String status;
    private final String message;
    private final int documentsCount;
    private final boolean notificationEmailSent;
    private final String notificationMessage;
}
