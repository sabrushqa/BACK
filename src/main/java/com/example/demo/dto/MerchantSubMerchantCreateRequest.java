package com.example.demo.dto;

public record MerchantSubMerchantCreateRequest(
    Long pdvId,
    // "SITE_MARCHAND" or "APPLICATION_MOBILE" — used instead of pdvId for
    // e-commerce merchants, who have no point de vente.
    String canalEcommerce,
    String nom,
    String prenom,
    String email,
    String telephone
) {
}
