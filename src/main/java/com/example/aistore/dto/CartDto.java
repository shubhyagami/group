package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
        Long id,
        List<CartItemDto> items,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal totalDiscount
) {
}