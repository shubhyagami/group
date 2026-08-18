package com.example.aistore.service;

import com.example.aistore.dto.AdminAnalyticsDto;
import com.example.aistore.entity.CustomerFeedback;
import com.example.aistore.entity.User;
import com.example.aistore.repository.CustomerFeedbackRepository;
import com.example.aistore.repository.InventoryRepository;
import com.example.aistore.repository.OrderItemRepository;
import com.example.aistore.repository.OrderRepository;
import com.example.aistore.repository.ProductRepository;
import com.example.aistore.repository.ReviewRepository;
import com.example.aistore.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAnalyticsService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final CustomerFeedbackRepository feedbackRepository;
    private final CustomerFeedbackService feedbackService;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;

    public AdminAnalyticsService(OrderRepository orderRepository, UserRepository userRepository,
                                 ProductRepository productRepository, ReviewRepository reviewRepository,
                                 CustomerFeedbackRepository feedbackRepository,
                                 CustomerFeedbackService feedbackService,
                                 OrderItemRepository orderItemRepository,
                                 InventoryRepository inventoryRepository,
                                 ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.feedbackRepository = feedbackRepository;
        this.feedbackService = feedbackService;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsDto getAnalytics() {
        List<Map<String, Object>> churnSignals = detectChurnRisks();
        List<Map<String, Object>> lowStock = new ArrayList<>();
        inventoryRepository.findAll().stream()
                .filter(inv -> inv.getStockQuantity() <= inv.getLowStockThreshold())
                .forEach(inv -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", inv.getProduct().getId());
                    row.put("productName", inv.getProduct().getName());
                    row.put("stockQuantity", inv.getStockQuantity());
                    row.put("lowStockThreshold", inv.getLowStockThreshold());
                    lowStock.add(row);
                });

        List<Map<String, Object>> topSelling = new ArrayList<>();
        for (Object[] row : orderItemRepository.topSellingProducts()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productName", row[0]);
            entry.put("quantitySold", row[1]);
            topSelling.add(entry);
        }

        return new AdminAnalyticsDto(
                orderRepository.sumRevenue(),
                orderRepository.sumDeliveredRevenue(),
                orderRepository.avgOrderValue(),
                orderRepository.count(),
                userRepository.count(),
                productRepository.count(),
                reviewRepository.count(),
                feedbackRepository.count(),
                feedbackService.countBySentimentGroup(),
                feedbackService.countByEmotionGroup(),
                feedbackService.countByTopicGroup(),
                feedbackService.countNegativeIssuesByTopic(),
                feedbackService.findProductsWithMostNegativeFeedback(),
                topSelling,
                toMap(orderRepository.countByStatusGroup()),
                churnSignals,
                lowStock);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> detectChurnRisks() {
        List<Map<String, Object>> risks = new ArrayList<>();
        List<Object[]> negativeByUser = feedbackRepository.findProductsWithMostNegativeFeedback();
        if (negativeByUser != null && !negativeByUser.isEmpty()) {
            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("signal", "Negative feedback concentration on key products");
            signal.put("detail", negativeByUser.stream().limit(3)
                    .map(r -> r[1] + " (" + r[2] + " negatives)").toList());
            risks.add(signal);
        }
        Map<String, Long> statuses = toMap(orderRepository.countByStatusGroup());
        long cancellations = statuses.getOrDefault("CANCELLED", 0L) + statuses.getOrDefault("RETURNED", 0L);
        if (cancellations > 0) {
            Map<String, Object> signal = new LinkedHashMap<>();
            signal.put("signal", "Cancellation & return volume");
            signal.put("detail", cancellations + " cancelled/returned orders suggest friction in delivery or product fit");
            risks.add(signal);
        }
        return risks;
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public String buildContextJson() {
        try {
            AdminAnalyticsDto dto = getAnalytics();
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            return "{}";
        }
    }
}