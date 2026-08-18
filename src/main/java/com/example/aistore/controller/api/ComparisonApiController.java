package com.example.aistore.controller.api;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.ProductComparisonDto;
import com.example.aistore.service.ProductComparisonService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/compare")
public class ComparisonApiController {

    private final ProductComparisonService comparisonService;

    public ComparisonApiController(ProductComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping("/data")
    public ApiResponse<ProductComparisonDto> compare(@RequestParam String ids) {
        List<Long> productIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
        return ApiResponse.ok(comparisonService.compare(productIds));
    }
}