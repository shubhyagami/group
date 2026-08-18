package com.example.aistore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TelemetryRequest(
        @NotBlank String sessionId,
        @NotBlank String interactionType,
        Long productId,
        String categoryName,
        String brandName,
        String searchQuery,
        int durationSeconds
) {
}