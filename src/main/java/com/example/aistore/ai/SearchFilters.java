package com.example.aistore.ai;

import java.math.BigDecimal;
import java.util.Set;

public record SearchFilters(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Set<String> categories,
        Set<String> brands,
        Double minRating,
        Set<String> tags,
        String freeText
) {
    public static SearchFilters empty() {
        return new SearchFilters(null, null, Set.of(), Set.of(), null, Set.of(), null);
    }

    public boolean isEmpty() {
        return minPrice == null && maxPrice == null && minRating == null
                && (categories == null || categories.isEmpty())
                && (brands == null || brands.isEmpty())
                && (tags == null || tags.isEmpty())
                && (freeText == null || freeText.isBlank());
    }
}