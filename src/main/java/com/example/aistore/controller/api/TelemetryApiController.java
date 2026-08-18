package com.example.aistore.controller.api;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.TelemetryRequest;
import com.example.aistore.entity.UserInteraction;
import com.example.aistore.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryApiController {

    private final TelemetryService telemetryService;
    private final com.example.aistore.service.UserService userService;

    public TelemetryApiController(TelemetryService telemetryService,
                                  com.example.aistore.service.UserService userService) {
        this.telemetryService = telemetryService;
        this.userService = userService;
    }

    @PostMapping("/interaction")
    public ApiResponse<Long> recordInteraction(@Valid @RequestBody TelemetryRequest request,
                                               Authentication authentication) {
        Long userId = null;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            try {
                userId = userService.findUserIdByEmail(authentication.getName());
            } catch (Exception ignored) {
            }
        }
        UserInteraction saved = telemetryService.recordInteraction(userId, request);
        return ApiResponse.ok("Interaction recorded", saved.getId());
    }
}