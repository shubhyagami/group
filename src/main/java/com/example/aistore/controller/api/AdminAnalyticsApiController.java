package com.example.aistore.controller.api;

import com.example.aistore.ai.AIProvider;
import com.example.aistore.dto.AdminAnalyticsDto;
import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.AskAiRequest;
import com.example.aistore.service.AdminAnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsApiController {

    private final AdminAnalyticsService analyticsService;
    private final AIProvider aiProvider;

    public AdminAnalyticsApiController(AdminAnalyticsService analyticsService, AIProvider aiProvider) {
        this.analyticsService = analyticsService;
        this.aiProvider = aiProvider;
    }

    @GetMapping("/analytics-data")
    public ApiResponse<AdminAnalyticsDto> analytics() {
        return ApiResponse.ok(analyticsService.getAnalytics());
    }

    @PostMapping("/ask-ai")
    public ApiResponse<Map<String, String>> askAi(@RequestBody AskAiRequest request) {
        String context = analyticsService.buildContextJson();
        String answer = aiProvider.answerAdminQuery(request.question(), context);
        if (answer == null || answer.isBlank()) {
            answer = "I couldn't complete that analysis with the configured AI provider. "
                    + "Please verify the API keys (ai.nvidia.api-keys) and try again, or check the full "
                    + "analytics dashboard for the requested metrics.";
        }
        return ApiResponse.ok(Map.of("answer", answer, "provider", aiProvider.name()));
    }
}