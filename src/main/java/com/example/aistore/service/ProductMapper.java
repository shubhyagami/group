package com.example.aistore.service;

import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.dto.ProductDetailsDto;
import com.example.aistore.dto.ReviewDto;
import com.example.aistore.dto.SpecDto;
import com.example.aistore.entity.CustomerFeedback;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.ProductImage;
import com.example.aistore.entity.ProductSpecification;
import com.example.aistore.entity.Review;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProductMapper {

    public ProductCardDto toCard(Product p) {
        return toCard(p, null, 0);
    }

    public ProductCardDto toCard(Product p, String whyRecommended, double recommendationScore) {
        return new ProductCardDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getSku(),
                p.getPrice(),
                p.getOriginalPrice(),
                p.getDiscountPercentage(),
                p.getRating(),
                p.getReviewCount(),
                p.getPrimaryImageUrl(),
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getBrand() != null ? p.getBrand().getName() : null,
                parseTags(p.getTags()),
                p.isInStock(),
                p.getStock(),
                p.getShortDescription(),
                whyRecommended,
                recommendationScore);
    }

    public ProductDetailsDto toDetails(Product p) {
        List<SpecDto> specs = new ArrayList<>();
        for (ProductSpecification s : p.getSpecifications()) {
            specs.add(new SpecDto(s.getSpecGroup(), s.getSpecKey(), s.getSpecValue(), s.getDisplayOrder()));
        }
        List<String> images = new ArrayList<>();
        if (p.getPrimaryImageUrl() != null) {
            images.add(p.getPrimaryImageUrl());
        }
        for (ProductImage i : p.getImages()) {
            images.add(i.getImageUrl());
        }
        List<ReviewDto> reviewDtos = new ArrayList<>();
        for (Review r : p.getReviews()) {
            reviewDtos.add(toReviewDto(r, null));
        }
        return new ProductDetailsDto(
                p.getId(), p.getName(), p.getSlug(), p.getSku(),
                p.getPrice(), p.getOriginalPrice(), p.getDiscountPercentage(),
                p.getRating(), p.getReviewCount(), p.getPrimaryImageUrl(),
                images,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getBrand() != null ? p.getBrand().getName() : null,
                p.getBrand() != null ? p.getBrand().getId() : null,
                parseTags(p.getTags()),
                p.isInStock(), p.getStock(), p.isFeatured(),
                p.getShortDescription(), p.getFullDescription(),
                specs, reviewDtos);
    }

    public ReviewDto toReviewDto(Review r, CustomerFeedback feedback) {
        return new ReviewDto(
                r.getId(),
                r.getProduct() != null ? r.getProduct().getId() : null,
                r.getUser() != null ? r.getUser().getFullName() : "Anonymous",
                r.getUser() != null ? r.getUser().getAvatarUrl() : null,
                r.getRating(),
                r.getTitle(),
                r.getComment(),
                r.isVerifiedPurchase(),
                r.getHelpfulCount(),
                r.getCreatedAt(),
                feedback != null ? feedback.getSentiment() : null,
                feedback != null ? feedback.getEmotion() : null);
    }

    public List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return List.of(tags.split(","));
    }

    public static <T> T firstOrNull(java.util.Optional<T> opt) {
        return opt.orElse(null);
    }
}