package com.example.aistore.dto;

public record OtpSendResponse(
        boolean success,
        String message,
        int expiresInSeconds,
        int attemptsRemaining,
        String devCode
) {
}