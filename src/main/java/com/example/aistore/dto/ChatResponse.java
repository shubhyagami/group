package com.example.aistore.dto;

import java.util.List;

public record ChatResponse(
        String conversationId,
        String message,
        String reasoningSummary,
        List<ProductCardDto> candidateProducts,
        List<String> followUpSuggestions,
        String toolUsed,
        String activeProvider,
        String status
) {
    public static ChatResponse ok(String conversationId, String message, String reasoningSummary,
                                  List<ProductCardDto> products, List<String> followUps,
                                  String toolUsed, String provider) {
        return new ChatResponse(conversationId, message, reasoningSummary, products, followUps, toolUsed, provider, "ok");
    }
}