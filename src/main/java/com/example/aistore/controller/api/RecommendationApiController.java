package com.example.aistore.controller.api;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.dto.RecommendationResponse;
import com.example.aistore.service.HybridRecommendationService;
import com.example.aistore.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationApiController {

    private final HybridRecommendationService recommendationService;
    private final UserService userService;

    public RecommendationApiController(HybridRecommendationService recommendationService,
                                       UserService userService) {
        this.recommendationService = recommendationService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<RecommendationResponse> recommend(
            @RequestParam(defaultValue = "8") int limit,
            Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Long userId = userService.findUserIdByEmail(authentication.getName());
            List<ProductCardDto> products = recommendationService.recommendForUser(userId, limit);
            return ApiResponse.ok(new RecommendationResponse(products, "hybrid",
                    "Scored by: 0.35*preference + 0.25*behavior + 0.20*content + 0.10*rating + 0.10*popularity"));
        }
        List<ProductCardDto> products = recommendationService.recommendForAnonymous(limit);
        return ApiResponse.ok(new RecommendationResponse(products, "popularity",
                "Anonymous mode: ranked by rating & popularity"));
    }
}