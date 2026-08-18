package com.example.aistore.dto;

public record CartActionRequest(
        Long productId,
        Integer quantity,
        String sessionId
) {
}