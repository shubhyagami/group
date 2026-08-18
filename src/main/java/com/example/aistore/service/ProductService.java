package com.example.aistore.service;

import com.example.aistore.ai.FeedbackAnalysis;
import com.example.aistore.ai.MockAIProvider;
import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.dto.ProductDetailsDto;
import com.example.aistore.dto.ReviewRequest;
import com.example.aistore.dto.ReviewDto;
import com.example.aistore.entity.CustomerFeedback;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.Review;
import com.example.aistore.entity.User;
import com.example.aistore.exception.BadRequestException;
import com.example.aistore.exception.ResourceNotFoundException;
import com.example.aistore.repository.CategoryRepository;
import com.example.aistore.repository.CustomerFeedbackRepository;
import com.example.aistore.repository.ProductRepository;
import com.example.aistore.repository.ReviewRepository;
import com.example.aistore.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final CustomerFeedbackRepository feedbackRepository;
    private final CustomerFeedbackService customerFeedbackService;
    private final MockAIProvider mockAIProvider;
    private final ProductMapper mapper;
    private final com.example.aistore.repository.UserRepository userRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          ReviewRepository reviewRepository, CustomerFeedbackRepository feedbackRepository,
                          CustomerFeedbackService customerFeedbackService, MockAIProvider mockAIProvider,
                          ProductMapper mapper, com.example.aistore.repository.UserRepository userRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.reviewRepository = reviewRepository;
        this.feedbackRepository = feedbackRepository;
        this.customerFeedbackService = customerFeedbackService;
        this.mockAIProvider = mockAIProvider;
        this.mapper = mapper;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    @Transactional(readOnly = true)
    public Page<ProductCardDto> listProducts(String categorySlug, String brand, String q,
                                             BigDecimal minPrice, BigDecimal maxPrice, Double minRating,
                                             String sort, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            if (categorySlug != null && !categorySlug.isBlank()) {
                var cat = categoryRepository.findBySlug(categorySlug)
                        .orElseThrow(() -> new ResourceNotFoundException("Category", categorySlug));
                predicates.add(cb.equal(root.get("category").get("id"), cat.getId()));
            }
            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("brand").get("name")), brand.toLowerCase(Locale.ROOT)));
            }
            if (q != null && !q.isBlank()) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), "%" + q.toLowerCase(Locale.ROOT) + "%"),
                        cb.like(cb.lower(root.get("tags")), "%" + q.toLowerCase(Locale.ROOT) + "%"),
                        cb.like(cb.lower(root.get("shortDescription")), "%" + q.toLowerCase(Locale.ROOT) + "%")));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (minRating != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort order = switch (sort == null ? "popularity" : sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "rating" -> Sort.by(Sort.Direction.DESC, "rating");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "discount" -> Sort.by(Sort.Direction.DESC, "discountPercentage");
            default -> Sort.by(Sort.Direction.DESC, "reviewCount");
        };
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(48, Math.max(1, size)), order);
        return productRepository.findAll(spec, pageable).map(mapper::toCard);
    }

    @Transactional(readOnly = true)
    public ProductDetailsDto getBySlug(String slug) {
        Product p = productRepository.findBySlug(slug)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        return mapper.toDetails(p);
    }

    @Transactional(readOnly = true)
    public Product getEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Transactional(readOnly = true)
    public List<ProductCardDto> getSimilar(Long productId, int limit) {
        Product p = getEntityById(productId);
        String[] tags = p.getTags() == null ? new String[0] : p.getTags().split(",");
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.notEqual(root.get("id"), p.getId()));
            if (p.getCategory() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), p.getCategory().getId()));
            }
            if (tags.length > 0) {
                Predicate[] tagPreds = new Predicate[tags.length];
                for (int i = 0; i < tags.length; i++) {
                    tagPreds[i] = cb.like(cb.lower(root.get("tags")), "%" + tags[i].trim().toLowerCase(Locale.ROOT) + "%");
                }
                predicates.add(cb.or(tagPreds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return productRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "rating")))
                .stream().map(mapper::toCard).toList();
    }

    @Transactional
    public ReviewDto submitReview(User user, Long productId, ReviewRequest request) {
        Product product = getEntityById(productId);
        if (reviewRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new BadRequestException("You have already reviewed this product");
        }
        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(request.rating());
        review.setTitle(request.title());
        review.setComment(request.comment());
        review.setVerifiedPurchase(true);
        review.setHelpfulCount(0);
        reviewRepository.save(review);

        CustomerFeedback feedback = customerFeedbackService.analyzeAndPersist(review);

        long count = reviewRepository.countByProductId(productId);
        double avg = reviewRepository.averageRatingForProduct(productId);
        product.setReviewCount((int) count);
        product.setRating(Math.round(avg * 10) / 10.0);
        productRepository.save(product);
        return mapper.toReviewDto(review, feedback);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> autocomplete(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Product> products = productRepository.searchByName(query.trim(), PageRequest.of(0, 6));
        List<Map<String, String>> result = new ArrayList<>();
        for (Product p : products) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("type", "product");
            entry.put("label", p.getName());
            entry.put("slug", p.getSlug());
            result.add(entry);
        }
        categoryRepository.findByNameContainingIgnoreCase(query.trim()).stream().limit(3).forEach(c -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("type", "category");
            entry.put("label", c.getName());
            entry.put("slug", c.getSlug());
            result.add(entry);
        });
        return result;
    }

    @Transactional(readOnly = true)
    public List<Product> findByNameContaining(String name, int limit) {
        return productRepository.searchByName(name, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public FeedbackAnalysis analyzeText(String text, int rating, String title) {
        return mockAIProvider.analyzeCustomerFeedback(text, rating, title);
    }
}