package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartItemDto(
        Long id,
        ProductCardDto product,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}