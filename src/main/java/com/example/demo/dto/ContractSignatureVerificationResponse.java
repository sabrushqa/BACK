package com.example.demo.dto;

public record ContractSignatureVerificationResponse(
    boolean signed,
    String message
) {
}
