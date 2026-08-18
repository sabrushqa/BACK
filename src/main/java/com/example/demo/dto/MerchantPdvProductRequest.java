package com.example.demo.dto;

public record MerchantPdvProductRequest(
    String nom,
    String adresse,
    String ville,
    String codePostal,
    String quartier,
    String telephone,
    String email,
    String typeAffiliation,
    String nombreTpe,
    String equipementTpe,
    String connectiviteTpe,
    String modeMiseADispositionTpe,
    String modeleQrSoftpos,
    String modeServiceEcommerce,
    String siteMarchandUrl,
    String applicationMobile,
    Double latitude,
    Double longitude,
    // Rempli quand le commercant choisit d'ajouter des terminaux sur un point
    // de vente qu'il possede DEJA, plutot que d'en creer un nouveau — dans ce
    // cas nom/adresse/ville/telephone/... ci-dessus sont ignores (le PDV
    // existe deja) et le dossier d'extension pointe directement vers ce PDV.
    Long existingPdvId
) {
}
