package com.example.demo.dto;

public record SupervisorPasswordChangeRequest(
    String currentPassword,
    String newPassword,
    String confirmPassword
) {
}
