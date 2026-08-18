package com.example.aistore.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AdminAnalyticsDto(
        BigDecimal totalRevenue,
        BigDecimal deliveredRevenue,
        BigDecimal avgOrderValue,
        long orderCount,
        long userCount,
        long productCount,
        long reviewCount,
        long feedbackCount,
        Map<String, Long> sentimentDistribution,
        Map<String, Long> emotionDistribution,
        Map<String, Long> topicDistribution,
        Map<String, Long> negativeIssuesByTopic,
        List<Map<String, Object>> productsWithMostNegativeFeedback,
        List<Map<String, Object>> topSellingProducts,
        Map<String, Long> orderStatusDistribution,
        List<Map<String, Object>> churnRiskSignals,
        List<Map<String, Object>> lowStockProducts
) {
}