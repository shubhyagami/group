package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record SearchFiltersDto(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Set<String> categories,
        Set<String> brands,
        Double minRating,
        Set<String> tags
) {
}