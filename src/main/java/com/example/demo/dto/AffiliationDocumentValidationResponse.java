package com.example.demo.dto;

public record AffiliationDocumentValidationResponse(
    String documentKey,
    String status,
    boolean supported,
    boolean performed,
    String expectedType,
    String detectedType,
    String reason,
    AffiliationRibExtractionResponse ribExtraction
) {
}
