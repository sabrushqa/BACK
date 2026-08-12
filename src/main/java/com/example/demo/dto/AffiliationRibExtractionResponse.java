package com.example.demo.dto;

public record AffiliationRibExtractionResponse(
    String rib,
    String banque,
    String titulaire,
    String codeBanque,
    String codeVille,
    String numeroCompte,
    String cleRib,
    String devise,
    String iban,
    String swift
) {
}
