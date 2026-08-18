package com.example.aistore.dto;

import java.util.List;
import java.util.Map;

public record ProductComparisonDto(
        List<ProductCardDto> products,
        Map<String, List<SpecComparisonRow>> specMatrix,
        Map<Long, Map<String, String>> priceComparison,
        String verdictSummary,
        Long bestPriceProductId,
        Long bestRatingProductId,
        Long bestValueProductId,
        List<String> keyDifferences
) {
    public record SpecComparisonRow(
            String group,
            String key,
            Map<Long, String> valuesByProductId
    ) {
    }
}