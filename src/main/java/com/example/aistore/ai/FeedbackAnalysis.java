package com.example.aistore.ai;

import java.util.List;

public record FeedbackAnalysis(
        String sentiment,
        String emotion,
        String primaryTopic,
        List<String> specificIssues,
        List<String> positiveAspects,
        double confidenceScore
) {
}