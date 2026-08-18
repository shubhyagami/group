package com.example.aistore.dto;

public record ChatRequest(
        String message,
        String conversationId,
        Long currentProductId,
        String sessionId
) {
}