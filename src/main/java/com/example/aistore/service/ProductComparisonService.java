package com.example.aistore.service;

import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.dto.ProductComparisonDto;
import com.example.aistore.dto.ProductComparisonDto.SpecComparisonRow;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.ProductSpecification;
import com.example.aistore.repository.ProductRepository;
import com.example.aistore.repository.ProductSpecificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multi-product hardware comparison engine &amp; spec-matrix builder.
 */
@Service
public class ProductComparisonService {

    private final ProductRepository productRepository;
    private final ProductSpecificationRepository specificationRepository;
    private final ProductMapper mapper;

    public ProductComparisonService(ProductRepository productRepository,
                                    ProductSpecificationRepository specificationRepository,
                                    ProductMapper mapper) {
        this.productRepository = productRepository;
        this.specificationRepository = specificationRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ProductComparisonDto compare(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 6) {
            throw new com.example.aistore.exception.BadRequestException("Provide between 1 and 6 product ids");
        }
        List<Product> products = productRepository.findByIds(ids);
        if (products.size() != new LinkedHashSet<>(ids).size()) {
            throw new com.example.aistore.exception.ResourceNotFoundException("One or more products were not found");
        }

        List<ProductCardDto> cards = products.stream().map(mapper::toCard).toList();

        Map<String, List<SpecComparisonRow>> specMatrix = buildSpecMatrix(products);

        Map<Long, Map<String, String>> priceComparison = new LinkedHashMap<>();
        for (Product p : products) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("price", p.getPrice().toPlainString());
            row.put("originalPrice", p.getOriginalPrice() != null ? p.getOriginalPrice().toPlainString() : "-");
            row.put("discount", p.getDiscountPercentage() != null ? p.getDiscountPercentage() + "%" : "0%");
            row.put("rating", String.valueOf(p.getRating()));
            row.put("reviewCount", String.valueOf(p.getReviewCount()));
            priceComparison.put(p.getId(), row);
        }

        Product bestPrice = products.stream().min(Comparator.comparing(Product::getPrice)).orElse(null);
        Product bestRating = products.stream().max(Comparator.comparing(Product::getRating)).orElse(null);
        Product bestValue = products.stream()
                .max(Comparator.comparingDouble(p -> p.getRating() / Math.max(1.0, p.getPrice().doubleValue())))
                .orElse(null);

        String verdict = buildVerdict(products, bestPrice, bestRating, bestValue);
        List<String> keyDifferences = findKeyDifferences(products, specMatrix);

        return new ProductComparisonDto(cards, specMatrix, priceComparison, verdict,
                bestPrice != null ? bestPrice.getId() : null,
                bestRating != null ? bestRating.getId() : null,
                bestValue != null ? bestValue.getId() : null,
                keyDifferences);
    }

    @Transactional(readOnly = true)
    public List<Product> compareRaw(List<Long> ids) {
        return productRepository.findByIds(ids);
    }

    private Map<String, List<SpecComparisonRow>> buildSpecMatrix(List<Product> products) {
        List<ProductSpecification> allSpecs = specificationRepository.findByProductIdInOrderByDisplayOrderAsc(
                products.stream().map(Product::getId).toList());

        Map<String, Map<String, SpecComparisonRow>> byGroup = new LinkedHashMap<>();
        Map<String, Set<String>> groupOrder = new LinkedHashMap<>();

        for (ProductSpecification spec : allSpecs) {
            String group = spec.getSpecGroup() == null ? "General" : spec.getSpecGroup();
            String key = spec.getSpecKey();
            String value = spec.getSpecValue() == null ? "-" : spec.getSpecValue();

            byGroup.computeIfAbsent(group, g -> new LinkedHashMap<>());
            groupOrder.computeIfAbsent(group, g -> new LinkedHashSet<>());

            SpecComparisonRow row = byGroup.get(group).computeIfAbsent(key, k ->
                    new SpecComparisonRow(group, key, new LinkedHashMap<>()));
            row.valuesByProductId().put(spec.getProduct().getId(), value);
            groupOrder.get(group).add(key);
        }

        Map<String, List<SpecComparisonRow>> matrix = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : groupOrder.entrySet()) {
            List<SpecComparisonRow> rows = new ArrayList<>();
            for (String key : entry.getValue()) {
                rows.add(byGroup.get(entry.getKey()).get(key));
            }
            matrix.put(entry.getKey(), rows);
        }
        return matrix;
    }

    private String buildVerdict(List<Product> products, Product bestPrice, Product bestRating, Product bestValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("Comparison verdict (rule-based, zero hallucination): ");
        if (products.size() == 1) {
            sb.append(products.get(0).getName()).append(" stands alone — check similar products for benchmarks.");
            return sb.toString();
        }
        sb.append(bestValue != null ? bestValue.getName() : "?")
                .append(" offers the best value (rating per rupee). ");
        if (bestPrice != null && bestPrice != bestValue) {
            sb.append(bestPrice.getName()).append(" is cheapest at ₹")
                    .append(bestPrice.getPrice().toPlainString()).append(". ");
        }
        if (bestRating != null) {
            sb.append(bestRating.getName()).append(" has the highest rating (")
                    .append(bestRating.getRating()).append("\u2605 across ").append(bestRating.getReviewCount())
                    .append(" reviews).");
        }
        return sb.toString();
    }

    private List<String> findKeyDifferences(List<Product> products, Map<String, List<SpecComparisonRow>> matrix) {
        List<String> differences = new ArrayList<>();
        for (List<SpecComparisonRow> rows : matrix.values()) {
            for (SpecComparisonRow row : rows) {
                Set<String> uniqueValues = new LinkedHashSet<>(row.valuesByProductId().values());
                if (uniqueValues.size() > 1 && uniqueValues.size() <= 3) {
                    String key = row.key();
                    if (key.equalsIgnoreCase("Processor") || key.equalsIgnoreCase("RAM")
                            || key.equalsIgnoreCase("Storage") || key.equalsIgnoreCase("Display")
                            || key.equalsIgnoreCase("Battery") || key.equalsIgnoreCase("Camera")
                            || key.equalsIgnoreCase("GPU")) {
                        StringBuilder sb = new StringBuilder(key).append(": ");
                        List<String> parts = new ArrayList<>();
                        for (Product p : products) {
                            parts.add(shortName(p.getName()) + " " + row.valuesByProductId().get(p.getId()));
                        }
                        sb.append(String.join(" vs ", parts));
                        differences.add(sb.toString());
                        if (differences.size() >= 5) {
                            break;
                        }
                    }
                }
            }
            if (differences.size() >= 5) {
                break;
            }
        }
        return differences;
    }

    private String shortName(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] words = name.split(" ");
        if (words.length <= 4) {
            return name;
        }
        return words[0] + " " + words[1] + " " + words[2] + "…";
    }
}