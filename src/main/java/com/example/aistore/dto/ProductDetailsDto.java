package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailsDto(
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
        List<String> imageUrls,
        String categoryName,
        Long categoryId,
        String brandName,
        Long brandId,
        List<String> tags,
        boolean inStock,
        int stock,
        boolean featured,
        String shortDescription,
        String fullDescription,
        List<SpecDto> specifications,
        List<ReviewDto> reviews
) {
}