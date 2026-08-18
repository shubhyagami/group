package com.example.aistore.service;

import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.User;
import com.example.aistore.entity.UserInteraction;
import com.example.aistore.entity.UserPreference;
import com.example.aistore.repository.ProductRepository;
import com.example.aistore.repository.UserInteractionRepository;
import com.example.aistore.repository.UserPreferenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid Recommendation Engine.
 * <p>
 * FinalScore(p) = w_pref*S_pref + w_beh*S_beh + w_cont*S_cont + w_rat*S_rat + w_pop*S_pop
 * with w_pref=0.35, w_beh=0.25, w_cont=0.20, w_rat=0.10, w_pop=0.10.
 */
@Service
public class HybridRecommendationService {

    private static final double W_PREF = 0.35;
    private static final double W_BEH = 0.25;
    private static final double W_CONT = 0.20;
    private static final double W_RAT = 0.10;
    private static final double W_POP = 0.10;

    private static final Map<String, Double> INTERACTION_WEIGHTS = Map.of(
            "PRODUCT_PURCHASE", 1.2,
            "ADD_TO_CART", 1.0,
            "ADD_TO_WISHLIST", 0.9,
            "PRODUCT_VIEW", 0.5,
            "SEARCH", 0.6,
            "PRODUCT_COMPARE", 0.8,
            "FILTER_APPLY", 0.4,
            "REMOVE_FROM_CART", -0.4);

    private final ProductRepository productRepository;
    private final UserInteractionRepository interactionRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final ProductMapper mapper;
    private final ObjectMapper objectMapper;

    public HybridRecommendationService(ProductRepository productRepository,
                                       UserInteractionRepository interactionRepository,
                                       UserPreferenceRepository preferenceRepository,
                                       ProductMapper mapper,
                                       ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.interactionRepository = interactionRepository;
        this.preferenceRepository = preferenceRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProductCardDto> recommendForUser(Long userId, int limit) {
        UserPreference preference = preferenceRepository.findByUserId(userId).orElse(null);
        if (preference == null || !preference.isRecommendationsEnabled()) {
            return popularFallback(limit);
        }

        List<UserInteraction> recent = interactionRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        List<Product> allProducts = productRepository.findAll().stream()
                .filter(Product::isActive)
                .filter(p -> p.getStock() > 0)
                .toList();
        long maxReviewCount = productRepository.maxReviewCount();
        if (maxReviewCount <= 0) {
            maxReviewCount = 1;
        }

        Map<String, Double> categoryAffinity = buildCategoryAffinity(recent);
        Map<String, Double> brandAffinity = buildBrandAffinity(recent);
        Set<String> seenProducts = new LinkedHashSet<>();
        for (UserInteraction i : recent) {
            if (i.getProductId() != null && (i.getInteractionType().name().equals("PRODUCT_PURCHASE")
                    || i.getInteractionType().name().equals("ADD_TO_CART"))) {
                seenProducts.add("p" + i.getProductId());
            }
        }

        List<ScoredProduct> scored = new ArrayList<>();
        for (Product p : allProducts) {
            if (seenProducts.contains("p" + p.getId())) {
                continue;
            }
            String category = p.getCategory() != null ? p.getCategory().getName() : "";
            String brand = p.getBrand() != null ? p.getBrand().getName() : "";

            double sPref = preferenceScore(p, preference, category, brand);
            double sBeh = behaviorScore(p, category, brand, categoryAffinity, brandAffinity);
            double sCont = contentScore(p, recent);
            double sRat = p.getRating() / 5.0;
            double sPop = p.getReviewCount() / (double) maxReviewCount;

            double finalScore = W_PREF * sPref + W_BEH * sBeh + W_CONT * sCont + W_RAT * sRat + W_POP * sPop;
            scored.add(new ScoredProduct(p, finalScore, sPref, sBeh, sCont, sRat, sPop));
        }

        scored.sort(Comparator.comparingDouble((ScoredProduct s) -> s.finalScore()).reversed());
        return scored.stream().limit(Math.min(limit, 20)).map(s -> {
            String why = buildWhyRecommended(s);
            return mapper.toCard(s.product(), why, Math.round(s.finalScore() * 1000) / 10.0);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductCardDto> recommendForAnonymous(int limit) {
        return popularFallback(limit);
    }

    private List<ProductCardDto> popularFallback(int limit) {
        long maxReviewCount = productRepository.maxReviewCount();
        List<Product> top = productRepository.findPopularInStock(PageRequest.of(0, Math.min(limit, 20)));
        return top.stream().map(p -> {
            double score = 0.5 * (p.getRating() / 5.0) + 0.5 * (p.getReviewCount() / (double) maxReviewCount);
            return mapper.toCard(p, "Top rated • Most popular in store", Math.round(score * 1000) / 10.0);
        }).toList();
    }

    private Map<String, Double> buildCategoryAffinity(List<UserInteraction> recent) {
        Map<String, Double> affinity = new HashMap<>();
        for (UserInteraction i : recent) {
            if (i.getCategoryName() == null) {
                continue;
            }
            double w = INTERACTION_WEIGHTS.getOrDefault(i.getInteractionType().name(), 0.3);
            affinity.merge(i.getCategoryName(), w, Double::sum);
        }
        return affinity;
    }

    private Map<String, Double> buildBrandAffinity(List<UserInteraction> recent) {
        Map<String, Double> affinity = new HashMap<>();
        for (UserInteraction i : recent) {
            if (i.getBrandName() == null) {
                continue;
            }
            double w = INTERACTION_WEIGHTS.getOrDefault(i.getInteractionType().name(), 0.3);
            affinity.merge(i.getBrandName(), w, Double::sum);
        }
        return affinity;
    }

    private double preferenceScore(Product p, UserPreference preference, String category, String brand) {
        double score = 0.0;
        double totalWeight = 0.0;

        Map<String, Integer> catPrefs = parseMap(preference.getPreferredCategoriesJson());
        if (!catPrefs.isEmpty()) {
            totalWeight += 1.0;
            if (catPrefs.containsKey(category)) {
                score += catPrefs.get(category) / 100.0;
            }
        }
        Map<String, Integer> brandPrefs = parseMap(preference.getPreferredBrandsJson());
        if (!brandPrefs.isEmpty()) {
            totalWeight += 1.0;
            if (brandPrefs.containsKey(brand)) {
                score += brandPrefs.get(brand) / 100.0;
            }
        }
        BigDecimal minBudget = preference.getMinBudget();
        BigDecimal maxBudget = preference.getMaxBudget();
        if (minBudget != null || maxBudget != null) {
            totalWeight += 1.0;
            if (minBudget != null && maxBudget != null) {
                if (p.getPrice().compareTo(minBudget) >= 0 && p.getPrice().compareTo(maxBudget) <= 0) {
                    score += 0.8;
                } else {
                    score += 0.15;
                }
            } else if (minBudget != null && p.getPrice().compareTo(minBudget) >= 0) {
                score += 0.8;
            } else if (maxBudget != null && p.getPrice().compareTo(maxBudget) <= 0) {
                score += 0.8;
            }
        }
        return totalWeight == 0 ? 0.4 : score / totalWeight;
    }

    private double behaviorScore(Product p, String category, String brand,
                                 Map<String, Double> categoryAffinity, Map<String, Double> brandAffinity) {
        double catScore = categoryAffinity.getOrDefault(category, 0.0);
        double brandScore = brandAffinity.getOrDefault(brand, 0.0);
        double maxCat = categoryAffinity.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double maxBrand = brandAffinity.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double catNorm = maxCat > 0 ? Math.min(1.0, catScore / maxCat) : 0;
        double brandNorm = maxBrand > 0 ? Math.min(1.0, brandScore / maxBrand) : 0;
        return Math.min(1.0, 0.7 * catNorm + 0.3 * brandNorm);
    }

    private double contentScore(Product p, List<UserInteraction> recent) {
        if (p.getTags() == null || p.getTags().isBlank()) {
            return 0.1;
        }
        Set<String> tagSet = Set.of(p.getTags().toLowerCase().split(","));
        Set<String> userTags = new LinkedHashSet<>();
        for (UserInteraction i : recent) {
            if (i.getSearchQuery() != null) {
                for (String word : i.getSearchQuery().toLowerCase().split("[^a-z0-9]+")) {
                    if (tagSet.contains(word)) {
                        userTags.add(word);
                    }
                }
            }
            if (i.getCategoryName() != null) {
                String catWord = i.getCategoryName().toLowerCase();
                if (tagSet.contains(catWord)) {
                    userTags.add(catWord);
                }
            }
        }
        return Math.min(1.0, userTags.size() / 2.0);
    }

    private String buildWhyRecommended(ScoredProduct s) {
        List<String> reasons = new ArrayList<>();
        String category = s.product().getCategory() != null ? s.product().getCategory().getName() : null;
        String brand = s.product().getBrand() != null ? s.product().getBrand().getName() : null;
        if (category != null && s.sPref() >= 0.6) {
            reasons.add("Matches preferred category (" + category + ")");
        }
        if (brand != null && s.sPref() >= 0.6) {
            reasons.add("Preferred brand (" + brand + ")");
        }
        if (s.sBeh() >= 0.5) {
            reasons.add("Similar to items you browsed");
        }
        if (s.product().getRating() >= 4.5) {
            reasons.add("Top rated (" + s.product().getRating() + "\u2605)");
        }
        if (s.product().isDiscounted()) {
            reasons.add(s.product().getDiscountPercentage() + "% off");
        }
        if (reasons.isEmpty()) {
            reasons.add("Popular choice");
        }
        return String.join(" \u2022 ", reasons);
    }

    private Map<String, Integer> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private record ScoredProduct(Product product, double finalScore, double sPref, double sBeh,
                                 double sCont, double sRat, double sPop) {
    }
}