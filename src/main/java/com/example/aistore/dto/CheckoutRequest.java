package com.example.aistore.dto;

public record CheckoutRequest(
        Long addressId,
        String paymentMethod,
        String sessionId
) {
}