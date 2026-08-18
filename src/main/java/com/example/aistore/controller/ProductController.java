package com.example.aistore.controller;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.dto.ProductDetailsDto;
import com.example.aistore.dto.ReviewDto;
import com.example.aistore.dto.ReviewRequest;
import com.example.aistore.entity.User;
import com.example.aistore.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<Page<ProductCardDto>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "popularity") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(productService.listProducts(category, brand, q, minPrice, maxPrice,
                minRating, sort, page, size));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ProductDetailsDto> details(@PathVariable String slug) {
        return ApiResponse.ok(productService.getBySlug(slug));
    }

    @GetMapping("/{id}/similar")
    public ApiResponse<List<ProductCardDto>> similar(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(productService.getSimilar(id, limit));
    }

    @PostMapping("/{id}/reviews")
    public ApiResponse<ReviewDto> addReview(Authentication authentication,
                                            @PathVariable Long id,
                                            @Valid @RequestBody ReviewRequest request) {
        String email = authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String)
                ? authentication.getName() : null;
        return ApiResponse.ok(productService.submitReview(resolveUser(email), id, request));
    }

    private User resolveUser(String email) {
        if (email == null) {
            throw new com.example.aistore.exception.BadRequestException("Please log in to submit a review");
        }
        return productService.findUserByEmail(email);
    }
}