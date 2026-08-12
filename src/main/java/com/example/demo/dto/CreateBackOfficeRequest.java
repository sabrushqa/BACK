package com.example.demo.dto;

public record CreateBackOfficeRequest(
    String nom,
    String prenom,
    String email,
    String matricule,
    String service,
    Boolean peutValiderDossiers,
    Boolean peutAffecterTpe,
    Boolean peutGererReclamations
) {
}
