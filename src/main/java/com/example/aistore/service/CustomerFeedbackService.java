package com.example.aistore.service;

import com.example.aistore.ai.FeedbackAnalysis;
import com.example.aistore.ai.MockAIProvider;
import com.example.aistore.dto.FeedbackSummaryDto;
import com.example.aistore.entity.CustomerFeedback;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.Review;
import com.example.aistore.repository.CustomerFeedbackRepository;
import com.example.aistore.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customer review sentiment &amp; emotion NLP intelligence service.
 * Analyzes every incoming review with the AI layer and persists structured telemetry
 * used by the admin dashboard and the conversational assistant.
 */
@Service
public class CustomerFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(CustomerFeedbackService.class);

    private final CustomerFeedbackRepository feedbackRepository;
    private final ProductRepository productRepository;
    private final MockAIProvider mockAIProvider;
    private final ObjectMapper objectMapper;

    public CustomerFeedbackService(CustomerFeedbackRepository feedbackRepository,
                                   ProductRepository productRepository,
                                   MockAIProvider mockAIProvider,
                                   ObjectMapper objectMapper) {
        this.feedbackRepository = feedbackRepository;
        this.productRepository = productRepository;
        this.mockAIProvider = mockAIProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CustomerFeedback analyzeAndPersist(Review review) {
        FeedbackAnalysis analysis = mockAIProvider.analyzeCustomerFeedback(
                review.getComment(), review.getRating(), review.getTitle());

        CustomerFeedback feedback = new CustomerFeedback();
        feedback.setReview(review);
        feedback.setProduct(review.getProduct());
        feedback.setUser(review.getUser());
        feedback.setSentiment(analysis.sentiment());
        feedback.setEmotion(analysis.emotion());
        feedback.setPrimaryTopic(analysis.primaryTopic());
        feedback.setSpecificIssuesJson(toJson(analysis.specificIssues()));
        feedback.setPositiveAspectsJson(toJson(analysis.positiveAspects()));
        feedback.setConfidenceScore(Math.round(analysis.confidenceScore() * 100.0) / 100.0);
        feedback.setSource("REVIEW");
        return feedbackRepository.save(feedback);
    }

    @Transactional
    public CustomerFeedback analyzeTextAndPersist(Long productId, Review reviewOrNull,
                                                  String text, int rating, String title) {
        FeedbackAnalysis analysis = mockAIProvider.analyzeCustomerFeedback(text, rating, title);
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return null;
        }
        CustomerFeedback feedback = new CustomerFeedback();
        feedback.setReview(reviewOrNull);
        feedback.setProduct(product);
        feedback.setUser(reviewOrNull != null ? reviewOrNull.getUser() : null);
        feedback.setSentiment(analysis.sentiment());
        feedback.setEmotion(analysis.emotion());
        feedback.setPrimaryTopic(analysis.primaryTopic());
        feedback.setSpecificIssuesJson(toJson(analysis.specificIssues()));
        feedback.setPositiveAspectsJson(toJson(analysis.positiveAspects()));
        feedback.setConfidenceScore(Math.round(analysis.confidenceScore() * 100.0) / 100.0);
        feedback.setSource("REVIEW");
        return feedbackRepository.save(feedback);
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    // ========================================================================
    // ADMIN AGGREGATIONS
    // ========================================================================

    @Transactional(readOnly = true)
    public Map<String, Long> countBySentimentGroup() {
        return toMap(feedbackRepository.countBySentimentGroup());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countByEmotionGroup() {
        return toMap(feedbackRepository.countByEmotionGroup());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countByTopicGroup() {
        return toMap(feedbackRepository.countByTopicGroup());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countNegativeIssuesByTopic() {
        return toMap(feedbackRepository.countNegativeIssuesByTopic());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findProductsWithMostNegativeFeedback() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : feedbackRepository.findProductsWithMostNegativeFeedback()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productId", row[0]);
            entry.put("productName", row[1]);
            entry.put("negativeCount", row[2]);
            result.add(entry);
        }
        return result;
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public FeedbackSummaryDto getProductFeedbackSummary(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            throw new com.example.aistore.exception.ResourceNotFoundException("Product", productId);
        }
        List<CustomerFeedback> all = feedbackRepository.findByProductId(productId);
        Map<String, Long> sentimentCounts = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        List<String> positives = new ArrayList<>();
        String dominant = "Neutral";
        long max = 0;
        for (CustomerFeedback fb : all) {
            sentimentCounts.merge(fb.getSentiment(), 1L, Long::sum);
            if (fb.getSentiment().equals("Negative") && issues.size() < 6) {
                issues.addAll(fromJson(fb.getSpecificIssuesJson()));
            }
            if ((fb.getSentiment().equals("Positive") || fb.getSentiment().equals("Mixed")) && positives.size() < 6) {
                positives.addAll(fromJson(fb.getPositiveAspectsJson()));
            }
            if (sentimentCounts.get(fb.getSentiment()) > max) {
                max = sentimentCounts.get(fb.getSentiment());
                dominant = fb.getSentiment();
            }
        }
        String summary = buildSummaryText(product.getName(), dominant, all.size(), issues);
        return new FeedbackSummaryDto(productId, product.getName(), all.size(), sentimentCounts,
                dominant, issues.stream().distinct().limit(5).toList(),
                positives.stream().distinct().limit(5).toList(), summary);
    }

    private String buildSummaryText(String productName, String dominant, int total, List<String> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append(productName).append(" has ").append(total).append(" analyzed feedback entries; ");
        sb.append("dominant sentiment is ").append(dominant.toLowerCase()).append(".");
        if (!issues.isEmpty()) {
            sb.append(" Recurring concerns include: ").append(String.join("; ", issues.stream().distinct().limit(3).toList())).append(".");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return feedbackRepository.count();
    }
}