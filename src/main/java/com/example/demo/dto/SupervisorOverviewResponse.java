package com.example.demo.dto;

import java.time.LocalDate;
import java.util.List;

public record SupervisorOverviewResponse(
    List<BackOfficeItem> backOffices,
    List<CommercialeItem> commerciales,
    List<CommercantItem> commercants
) {

    public record BackOfficeItem(
        Long id,
        Long utilisateurId,
        String nom,
        String prenom,
        String email,
        String matricule,
        String service,
        String role,
        boolean active,
        LocalDate dateCreation,
        LocalDate dateActivation
    ) {
    }

    public record CommercialeItem(
        Long id,
        Long utilisateurId,
        String nom,
        String prenom,
        String email,
        String matricule,
        String region,
        String telephone,
        String role,
        boolean active,
        LocalDate dateCreation,
        LocalDate dateActivation
    ) {
    }

    public record CommercantItem(
        Long id,
        Long utilisateurId,
        String nom,
        String email,
        String typeCommercant,
        String typeAffiliation,
        String activite,
        String ville,
        String region,
        String telephone,
        boolean active,
        LocalDate dateCreation,
        LocalDate dateActivation
    ) {
    }
}
