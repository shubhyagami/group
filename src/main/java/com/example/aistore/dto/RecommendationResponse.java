package com.example.aistore.dto;

import java.util.List;

public record RecommendationResponse(
        List<ProductCardDto> products,
        String strategy,
        String explanation
) {
}