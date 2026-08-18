package com.example.aistore.ai;

import com.example.aistore.config.LocalAIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Local OpenAI-compatible endpoint provider (Ollama / vLLM / LocalAI) for
 * offline high-performance inference.
 */
@Component
public class LocalAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalAIProvider.class);

    private final LocalAIProperties properties;
    private final WebClient webClient;
    private final MockAIProvider mockFallback;

    public LocalAIProvider(LocalAIProperties properties, WebClient.Builder webClientBuilder,
                           MockAIProvider mockFallback) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
        this.mockFallback = mockFallback;
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public String chatCompletion(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "temperature", 0.4,
                    "max_tokens", 800);
            String raw = webClient.post()
                    .uri("/chat/completions")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(4000))
                    .block();
            if (raw != null && !raw.isBlank()) {
                return extractContent(raw);
            }
        } catch (Exception e) {
            log.warn("[LocalAI] Local inference unavailable: {}", e.getMessage());
        }
        return null;
    }

    private String extractContent(String raw) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readTree(raw).path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            return raw;
        }
    }

    @Override
    public SearchFilters parseNaturalLanguageSearch(String query) {
        return mockFallback.parseNaturalLanguageSearch(query);
    }

    @Override
    public FeedbackAnalysis analyzeCustomerFeedback(String text, int rating, String title) {
        return mockFallback.analyzeCustomerFeedback(text, rating, title);
    }

    @Override
    public String answerAdminQuery(String question, String contextJson) {
        return mockFallback.answerAdminQuery(question, contextJson);
    }
}