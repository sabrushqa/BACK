package com.example.demo.dto;

public record MerchantSubMerchantCreateResponse(
    Long id,
    String message,
    boolean activationEmailSent,
    String activationMessage
) {
}
