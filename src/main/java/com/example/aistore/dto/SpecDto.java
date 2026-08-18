package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.List;

public record SpecDto(
        String group,
        String key,
        String value,
        int displayOrder
) {
}