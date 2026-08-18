package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductCardDto(
        Long id,
        String name,
        String slug,
        String sku,
        BigDecimal price,
        BigDecimal originalPrice,
        BigDecimal discountPercentage,
        double rating,
        int reviewCount,
        String primaryImageUrl,
        String categoryName,
        String brandName,
        List<String> tags,
        boolean inStock,
        int stock,
        String shortDescription,
        String whyRecommended,
        double recommendationScore
) {
}