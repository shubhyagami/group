package com.example.aistore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String secret,
        Otp otp,
        Shipping shipping
) {
    public record Otp(
            int ttlMinutes,
            int maxAttempts,
            int digits,
            boolean devMode
    ) {
    }

    public record Shipping(
            BigDecimal freeAbove,
            BigDecimal fee
    ) {
    }
}