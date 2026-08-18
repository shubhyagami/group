package com.example.aistore.dto;

import java.time.LocalDateTime;

public record ReviewDto(
        Long id,
        Long productId,
        String userName,
        String avatarUrl,
        int rating,
        String title,
        String comment,
        boolean verifiedPurchase,
        int helpfulCount,
        LocalDateTime createdAt,
        String sentiment,
        String emotion
) {
}