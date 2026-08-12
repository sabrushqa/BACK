package com.example.demo.dto;

public record GoogleCalendarStatusResponse(
    boolean configured,
    boolean connected,
    String message
) {
}
