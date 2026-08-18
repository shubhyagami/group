package com.example.aistore.ai;

import com.example.aistore.dto.FeedbackSummaryDto;
import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.entity.Product;
import com.example.aistore.service.CustomerFeedbackService;
import com.example.aistore.service.HybridRecommendationService;
import com.example.aistore.service.NaturalLanguageSearchService;
import com.example.aistore.service.ProductComparisonService;
import com.example.aistore.service.ProductService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Safe database tool routing for the AI orchestrator.
 * Every tool reads ONLY verified database facts - guaranteeing zero hallucination.
 */
@Component
public class ToolRouter {

    private final NaturalLanguageSearchService searchService;
    private final ProductComparisonService comparisonService;
    private final CustomerFeedbackService feedbackService;
    private final HybridRecommendationService recommendationService;
    private final ProductService productService;

    public ToolRouter(NaturalLanguageSearchService searchService,
                      ProductComparisonService comparisonService,
                      CustomerFeedbackService feedbackService,
                      HybridRecommendationService recommendationService,
                      ProductService productService) {
        this.searchService = searchService;
        this.comparisonService = comparisonService;
        this.feedbackService = feedbackService;
        this.recommendationService = recommendationService;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public List<Product> searchProducts(SearchFilters filters, int limit) {
        return searchService.findProducts(filters, limit);
    }

    @Transactional(readOnly = true)
    public List<Product> compareProducts(List<Long> productIds) {
        return comparisonService.compareRaw(productIds);
    }

    @Transactional(readOnly = true)
    public FeedbackSummaryDto getProductFeedbackSummary(Long productId) {
        return feedbackService.getProductFeedbackSummary(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductCardDto> getRecommendedProducts(Long userId, int limit) {
        if (userId != null) {
            return recommendationService.recommendForUser(userId, limit);
        }
        return recommendationService.recommendForAnonymous(limit);
    }
}