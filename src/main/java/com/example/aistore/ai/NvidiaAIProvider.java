package com.example.aistore.ai;

import com.example.aistore.config.NvidiaAIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * NVIDIA Nemotron API provider with a sequential multi-key failover pool.
 * Each key is attempted in order; on HTTP 401/429/5xx or timeout (2500ms) the next key
 * is tried. If every key fails, {@code null} is returned so the orchestrator can
 * seamlessly fall back to the deterministic mock engine.
 */
@Component
public class NvidiaAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(NvidiaAIProvider.class);

    private final NvidiaAIProperties properties;
    private final WebClient webClient;
    private final MockAIProvider mockFallback;

    public NvidiaAIProvider(NvidiaAIProperties properties, WebClient.Builder webClientBuilder,
                            MockAIProvider mockFallback) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
        this.mockFallback = mockFallback;
    }

    @Override
    public String name() {
        return "nvidia";
    }

    /**
     * Iterates the configured key pool sequentially. Returns the first successful
     * completion text, or {@code null} when every key failed.
     */
    public String executeWithFailover(Map<String, Object> body) {
        String[] keys = properties.keyPool();
        if (keys.length == 0) {
            log.warn("[NvidiaAI] No API keys configured - triggering fallback");
            return null;
        }
        for (String key : keys) {
            try {
                String result = callNvidia(key, body);
                if (result != null && !result.isBlank()) {
                    log.info("[NvidiaAI] Success with key {} ({} keys in pool)", mask(key), keys.length);
                    return result;
                }
            } catch (Exception e) {
                log.warn("[NvidiaAI] Key {} failed: {} - moving to next key", mask(key), safeMessage(e));
            }
        }
        log.warn("[NvidiaAI] All {} keys failed - falling back to local/mock providers", keys.length);
        return null;
    }

    private String callNvidia(String apiKey, Map<String, Object> body) {
        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(properties.timeoutMs()))
                .block();
    }

    private String mask(String key) {
        if (key == null || key.length() < 12) {
            return "****";
        }
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        if (msg.length() > 200) {
            msg = msg.substring(0, 200);
        }
        return msg;
    }

    // ========================================================================
    // AIProvider interface (with automatic degrade-to-mock semantics)
    // ========================================================================

    @Override
    public String chatCompletion(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.4,
                "max_tokens", 800,
                "top_p", 0.9);
        String text = executeWithFailover(body);
        if (text == null) {
            return null;
        }
        return extractContent(text);
    }

    private String extractContent(String raw) {
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
            return root.path("choices").path(0).path("message").path("content").asText();
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
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are the OmniMart AI store analytics assistant. Answer the question using ONLY "
                                        + "the provided store context JSON. Be concise, cite exact numbers, and state "
                                        + "an actionable recommendation."),
                        Map.of("role", "user", "content",
                                "STORE CONTEXT:\n" + contextJson + "\n\nQUESTION: " + question)),
                "temperature", 0.3,
                "max_tokens", 600,
                "top_p", 0.9);
        String text = executeWithFailover(body);
        if (text == null) {
            return null;
        }
        return extractContent(text);
    }
}