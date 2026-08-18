package com.example.aistore.controller;

import com.example.aistore.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ApiResponse<Map<String, Object>> index() {
        return ApiResponse.ok("OmniMart AI backend is running", Map.of(
                "name", "OmniMart AI E-Commerce Backend",
                "status", "online",
                "endpoints", Map.of(
                        "chat", "/api/chat",
                        "recommendations", "/api/recommendations?limit=8",
                        "search", "/api/search/nl",
                        "compare", "/api/compare/data?ids=1,2,3",
                        "products", "/products",
                        "admin", "/api/admin/analytics-data",
                        "h2-console", "/h2-console")));
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "service", "omnimart-ai"));
    }
}