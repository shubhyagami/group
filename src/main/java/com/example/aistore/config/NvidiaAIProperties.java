package com.example.aistore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.nvidia")
public record NvidiaAIProperties(
        String apiKeys,
        String model,
        String baseUrl,
        long timeoutMs
) {
    public String[] keyPool() {
        if (apiKeys == null || apiKeys.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(apiKeys.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .toArray(String[]::new);
    }
}