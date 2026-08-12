package com.example.demo.dto;

public record MicrosoftCalendarStatusResponse(
    boolean configured,
    boolean connected,
    String message
) {
}
