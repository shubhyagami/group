package com.example.aistore.controller.api;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.SearchNlRequest;
import com.example.aistore.dto.SearchNlResponse;
import com.example.aistore.service.NaturalLanguageSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private final NaturalLanguageSearchService searchService;
    private final com.example.aistore.service.ProductService productService;

    public SearchApiController(NaturalLanguageSearchService searchService,
                               com.example.aistore.service.ProductService productService) {
        this.searchService = searchService;
        this.productService = productService;
    }

    @GetMapping("/autocomplete")
    public ApiResponse<List<Map<String, String>>> autocomplete(@RequestParam String q) {
        return ApiResponse.ok(productService.autocomplete(q));
    }

    @PostMapping("/nl")
    public ApiResponse<SearchNlResponse> naturalLanguage(@Valid @RequestBody SearchNlRequest request) {
        return ApiResponse.ok(searchService.search(request.query(), request.limit()));
    }
}