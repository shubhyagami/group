package com.example.aistore.ai;

/**
 * Abstraction over every AI backend (NVIDIA Nemotron, Local Ollama/vLLM, deterministic Mock).
 */
public interface AIProvider {

    String name();

    /**
     * Natural-language chat completion. Implementations MUST never throw — return null on failure
     * so the caller can fail over to the next provider.
     */
    String chatCompletion(String systemPrompt, String userPrompt);

    SearchFilters parseNaturalLanguageSearch(String query);

    FeedbackAnalysis analyzeCustomerFeedback(String text, int rating, String title);

    String answerAdminQuery(String question, String contextJson);
}