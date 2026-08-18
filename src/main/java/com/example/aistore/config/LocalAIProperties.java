package com.example.aistore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.local")
public record LocalAIProperties(
        String baseUrl,
        String model
) {
}