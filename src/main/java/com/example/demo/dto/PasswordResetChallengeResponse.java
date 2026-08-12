package com.example.demo.dto;

import java.time.OffsetDateTime;

public record PasswordResetChallengeResponse(
    String message,
    OffsetDateTime expiresAt,
    String deliveryHint
) {
}
