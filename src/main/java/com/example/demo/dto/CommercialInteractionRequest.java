package com.example.demo.dto;

public record CommercialInteractionRequest(
    String typeInteraction,
    String resultat,
    String commentaire,
    String statut,
    String dateInteraction,
    String prochaineRelanceDate,
    String prochaineRelanceType,
    String prospectStatus
) {
}
