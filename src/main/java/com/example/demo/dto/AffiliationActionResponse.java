package com.example.demo.dto;

public record AffiliationActionResponse(String message, Long dossierId) {

    public AffiliationActionResponse(String message) {
        this(message, null);
    }
}
