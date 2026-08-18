package com.example.aistore.dto;

import java.time.LocalDateTime;

public record AddressDto(
        Long id,
        String fullName,
        String streetAddress,
        String apartment,
        String city,
        String state,
        String postalCode,
        String country,
        String phone,
        String addressType,
        boolean isDefault,
        LocalDateTime createdAt
) {
}