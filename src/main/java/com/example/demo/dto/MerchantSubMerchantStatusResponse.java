package com.example.demo.dto;

public record MerchantSubMerchantStatusResponse(
    Long id,
    boolean active,
    String statut,
    String message
) {
}
