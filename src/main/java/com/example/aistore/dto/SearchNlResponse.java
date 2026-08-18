package com.example.aistore.dto;

import java.util.List;

public record SearchNlResponse(
        String query,
        SearchFiltersDto filters,
        List<ProductCardDto> products
) {
}