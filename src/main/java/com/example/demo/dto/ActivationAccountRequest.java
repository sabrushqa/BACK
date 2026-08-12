package com.example.demo.dto;

public record ActivationAccountRequest(
    String email,
    String temporaryPassword,
    String newPassword
) {
}
