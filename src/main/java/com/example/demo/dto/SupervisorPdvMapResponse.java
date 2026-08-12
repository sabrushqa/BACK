package com.example.demo.dto;

import java.util.List;

public record SupervisorPdvMapResponse(
    List<PdvMapItem> pdvs
) {

    public record PdvMapItem(
        Long idPdv,
        String nomPdv,
        String ville,
        String adresse,
        String quartier,
        String codePostal,
        Double latitude,
        Double longitude,
        String statut,
        String nomCommercant,
        String typeCommercant,
        String typeAffiliation,
        String region
    ) {
    }
}
