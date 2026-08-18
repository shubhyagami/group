package com.example.aistore.controller.api;

import com.example.aistore.ai.AIOrchestrator;
import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.ChatRequest;
import com.example.aistore.dto.ChatResponse;
import com.example.aistore.entity.User;
import com.example.aistore.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {

    private final AIOrchestrator orchestrator;
    private final UserService userService;

    public ChatApiController(AIOrchestrator orchestrator, UserService userService) {
        this.orchestrator = orchestrator;
        this.userService = userService;
    }

    @PostMapping
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request, Authentication authentication) {
        User user = resolveUser(authentication);
        return ApiResponse.ok(orchestrator.handleMessage(request, user));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        try {
            return userService.findByEmail(authentication.getName());
        } catch (Exception e) {
            return null;
        }
    }
}