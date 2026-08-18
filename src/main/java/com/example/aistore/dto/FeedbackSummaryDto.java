package com.example.aistore.dto;

import java.util.List;
import java.util.Map;

public record FeedbackSummaryDto(
        Long productId,
        String productName,
        long totalFeedbacks,
        Map<String, Long> sentimentDistribution,
        String dominantSentiment,
        List<String> keyNegativeIssues,
        List<String> keyPositiveAspects,
        String summaryText
) {
}