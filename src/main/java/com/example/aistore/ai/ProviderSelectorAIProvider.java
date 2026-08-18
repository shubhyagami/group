package com.example.aistore.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary {@link AIProvider} bean - dynamically delegates to the provider
 * configured via {@code ai.provider} (nvidia | local | mock), defaulting to nvidia.
 * NVIDIA itself internally degrades to the deterministic mock on total key failure.
 */
@Component
@Primary
public class ProviderSelectorAIProvider implements AIProvider {

    private final NvidiaAIProvider nvidiaProvider;
    private final LocalAIProvider localProvider;
    private final MockAIProvider mockProvider;
    private final String configuredProvider;

    public ProviderSelectorAIProvider(NvidiaAIProvider nvidiaProvider, LocalAIProvider localProvider,
                                      MockAIProvider mockProvider,
                                      @Qualifier("configuredAiProvider") String configuredProvider) {
        this.nvidiaProvider = nvidiaProvider;
        this.localProvider = localProvider;
        this.mockProvider = mockProvider;
        this.configuredProvider = configuredProvider == null ? "nvidia" : configuredProvider.toLowerCase();
    }

    private AIProvider delegate() {
        return switch (configuredProvider) {
            case "local" -> localProvider;
            case "mock", "offline" -> mockProvider;
            default -> nvidiaProvider;
        };
    }

    @Override
    public String name() {
        return delegate().name();
    }

    @Override
    public String chatCompletion(String systemPrompt, String userPrompt) {
        return delegate().chatCompletion(systemPrompt, userPrompt);
    }

    @Override
    public SearchFilters parseNaturalLanguageSearch(String query) {
        return delegate().parseNaturalLanguageSearch(query);
    }

    @Override
    public FeedbackAnalysis analyzeCustomerFeedback(String text, int rating, String title) {
        return delegate().analyzeCustomerFeedback(text, rating, title);
    }

    @Override
    public String answerAdminQuery(String question, String contextJson) {
        return delegate().answerAdminQuery(question, contextJson);
    }
}