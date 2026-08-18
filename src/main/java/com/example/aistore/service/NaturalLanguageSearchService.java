package com.example.aistore.service;

import com.example.aistore.ai.AIProvider;
import com.example.aistore.ai.SearchFilters;
import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.dto.SearchFiltersDto;
import com.example.aistore.dto.SearchNlResponse;
import com.example.aistore.entity.Product;
import com.example.aistore.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Natural-language search &amp; spec-extraction service. Uses the AI layer to parse
 * budget bounds, categories, brands, ratings and feature tags from free-form queries.
 */
@Service
public class NaturalLanguageSearchService {

    private final AIProvider aiProvider;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final TelemetryService telemetryService;

    public NaturalLanguageSearchService(AIProvider aiProvider, ProductRepository productRepository,
                                        ProductMapper mapper, TelemetryService telemetryService) {
        this.aiProvider = aiProvider;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.telemetryService = telemetryService;
    }

    @Transactional(readOnly = true)
    public SearchNlResponse search(String query, int limit) {
        SearchFilters filters = aiProvider.parseNaturalLanguageSearch(query);
        List<Product> products = findProducts(filters, limit);

        telemetryService.recordSearch(null, null, query, products.size());

        List<ProductCardDto> cards = products.stream().map(mapper::toCard).toList();
        return new SearchNlResponse(query, toDto(filters), cards);
    }

    @Transactional(readOnly = true)
    public List<Product> findProducts(SearchFilters filters, int limit) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.greaterThan(root.get("stock"), 0));

            if (filters.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filters.minPrice()));
            }
            if (filters.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filters.maxPrice()));
            }
            if (filters.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), filters.minRating()));
            }
            if (filters.categories() != null && !filters.categories().isEmpty()) {
                predicates.add(root.get("category").get("name").in(filters.categories()));
            }
            if (filters.brands() != null && !filters.brands().isEmpty()) {
                predicates.add(root.get("brand").get("name").in(filters.brands()));
            }
            if (filters.tags() != null && !filters.tags().isEmpty()) {
                List<Predicate> tagPreds = new ArrayList<>();
                for (String tag : filters.tags()) {
                    tagPreds.add(cb.like(cb.lower(root.get("tags")), "%" + tag.toLowerCase(Locale.ROOT) + "%"));
                }
                // OR semantics: category/tag/free-text hits are joined with OR so
                // "camera phone under 40000" recalls smartphones AND camera-tagged items.
                List<Predicate> softPreds = new ArrayList<>(tagPreds);
                if (filters.categories() != null && !filters.categories().isEmpty()) {
                    softPreds.add(root.get("category").get("name").in(filters.categories()));
                }
                if (filters.freeText() != null && !filters.freeText().isBlank()) {
                    String like = "%" + filters.freeText().toLowerCase(Locale.ROOT) + "%";
                    softPreds.add(cb.like(cb.lower(root.get("name")), like));
                }
                predicates.add(cb.or(softPreds.toArray(new Predicate[0])));
            } else if (filters.freeText() != null && !filters.freeText().isBlank()) {
                String like = "%" + filters.freeText().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("tags")), like),
                        cb.like(cb.lower(root.get("shortDescription")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return productRepository.findAll(spec,
                PageRequest.of(0, Math.min(limit, 20), Sort.by(Sort.Direction.DESC, "rating"))).getContent();
    }

    public SearchFiltersDto toDto(SearchFilters filters) {
        return new SearchFiltersDto(filters.minPrice(), filters.maxPrice(),
                filters.categories(), filters.brands(), filters.minRating(), filters.tags());
    }
}