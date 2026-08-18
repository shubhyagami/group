package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProfileResponse(
        UserDto user,
        List<AddressDto> addresses,
        Map<String, Integer> preferredCategories,
        Map<String, Integer> preferredBrands,
        BigDecimal minBudget,
        BigDecimal maxBudget,
        boolean recommendationsEnabled,
        boolean behaviorTrackingEnabled
) {
}