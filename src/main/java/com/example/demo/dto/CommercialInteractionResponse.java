package com.example.demo.dto;

import java.time.LocalDate;
import java.util.List;

public record CommercialInteractionResponse(
    List<CommercialInteractionItem> interactions,
    Boolean googleCalendarSynced,
    String googleCalendarMessage,
    String googleCalendarEventUrl,
    Boolean microsoftCalendarSynced,
    String microsoftCalendarMessage,
    String microsoftCalendarEventUrl
) {

    public CommercialInteractionResponse(List<CommercialInteractionItem> interactions) {
        this(interactions, null, null, null, null, null, null);
    }

    public record CommercialInteractionItem(
        Long interactionId,
        String typeInteraction,
        String resultat,
        String commentaire,
        String statut,
        LocalDate dateInteraction,
        LocalDate prochaineRelanceDate,
        String prochaineRelanceType,
        String prospectStatus,
        String commercialNom
    ) {
    }
}
