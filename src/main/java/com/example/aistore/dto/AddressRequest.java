package com.example.aistore.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
        @NotBlank String fullName,
        @NotBlank String streetAddress,
        String apartment,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String postalCode,
        @NotBlank String country,
        String phone,
        String addressType,
        boolean isDefault
) {
}