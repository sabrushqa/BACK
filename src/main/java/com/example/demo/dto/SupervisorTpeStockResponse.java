package com.example.demo.dto;

import java.time.LocalDate;
import java.util.List;

public record SupervisorTpeStockResponse(
    List<TpeStockItem> tpes
) {
    public record TpeStockItem(
        String id,
        String numeroSerie,
        String modele,
        String typeCompatible,
        String typeConnexion,
        String statut,
        boolean actif,
        String commerciale,
        Long commercialeId,
        String commercant,
        Long commercantId,
        String pdv,
        LocalDate dateActivation,
        LocalDate dateAffectationCommerciale
    ) {
    }
}
