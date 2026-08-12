package com.example.demo.dto;

import java.time.LocalDate;

public record MerchantContractOverviewResponse(
    Long dossierId,
    String dossierStatus,
    boolean contractDisponible,
    String contractFileName,
    LocalDate contractGeneratedAt,
    boolean signedContractDisponible,
    String signedContractFileName,
    LocalDate signedContractUploadedAt,
    String commercialAttribue
) {
}
