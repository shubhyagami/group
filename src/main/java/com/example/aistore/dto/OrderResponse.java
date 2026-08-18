package com.example.aistore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderItemResponseDto address,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal shippingFee,
        BigDecimal finalAmount,
        String status,
        String carrier,
        String trackingNumber,
        String paymentMethod,
        String paymentStatus,
        String transactionId,
        LocalDateTime estimatedDeliveryDate,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt,
        List<OrderLineDto> items
) {
    public record OrderItemResponseDto(
            Long id,
            String fullName,
            String streetAddress,
            String apartment,
            String city,
            String state,
            String postalCode,
            String country,
            String phone
    ) {
    }

    public record OrderLineDto(
            Long productId,
            String productName,
            String productImageUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) {
    }
}