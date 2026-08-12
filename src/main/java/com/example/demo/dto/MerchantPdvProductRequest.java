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
    Double longitude
) {
}
