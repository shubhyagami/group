package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.Map;

public record UpdatePreferencesRequest(
        Map<String, Integer> preferredCategories,
        Map<String, Integer> preferredBrands,
        BigDecimal minBudget,
        BigDecimal maxBudget,
        Boolean recommendationsEnabled,
        Boolean behaviorTrackingEnabled
) {
}