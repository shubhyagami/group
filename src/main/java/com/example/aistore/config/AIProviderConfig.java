package com.example.aistore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIProviderConfig {

    @Bean
    public String configuredAiProvider(@Value("${ai.provider:mock}") String provider) {
        return provider;
    }
}