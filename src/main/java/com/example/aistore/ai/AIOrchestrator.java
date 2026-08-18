package com.example.aistore.ai;

import com.example.aistore.config.AppProperties;
import com.example.aistore.dto.ChatRequest;
import com.example.aistore.dto.ChatResponse;
import com.example.aistore.dto.FeedbackSummaryDto;
import com.example.aistore.dto.ProductCardDto;
import com.example.aistore.entity.AIRecommendationLog;
import com.example.aistore.entity.ChatConversation;
import com.example.aistore.entity.ChatMessage;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.User;
import com.example.aistore.repository.AIRecommendationLogRepository;
import com.example.aistore.repository.ChatConversationRepository;
import com.example.aistore.repository.ChatMessageRepository;
import com.example.aistore.service.ProductMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Multi-turn conversational AI orchestrator with safe tool routing &amp; guardrails.
 * Maintains context in ChatConversation/ChatMessage, resolves cross-turn references
 * ("compare the top two"), executes only verified database tools, and persists
 * audit logs in AIRecommendationLog.
 */
@Service
public class AIOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrator.class);

    private static final Pattern COMPARE_KEYWORDS = Pattern.compile(
            "\\b(compare|comparison|versus|vs|difference|which (is|one) (better|best)|specs? comparison)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEEDBACK_KEYWORDS = Pattern.compile(
            "\\b(reviews?|feedback|what do customers say|complaints?|issues?|battery life|camera quality|problems?|ratings?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECOMMEND_KEYWORDS = Pattern.compile(
            "\\b(recommend|suggest|best .* for me|what should i buy|gift ideas?|top picks)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLLOWUP_KEYWORDS = Pattern.compile(
            "\\b(top two|top 2|top three|top 3|those|them|these|first two|that one|the (first|second|third)|compare them|its battery|its camera|that product|the top)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRODUCT_ID_REFERENCE = Pattern.compile(
            "(?:product|no\\.?|#)?\\s*(\\d{1,3})\\s*(?:star|\\u2605)?");

    private static final Map<String, ConversationState> STATE_STORE = new ConcurrentHashMap<>();

    private final ToolRouter toolRouter;
    private final AIProvider primaryProvider;
    private final AIProvider localProvider;
    private final AIProvider mockProvider;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final AIRecommendationLogRepository logRepository;
    private final ProductMapper mapper;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public AIOrchestrator(ToolRouter toolRouter, NvidiaAIProvider nvidiaProvider,
                          LocalAIProvider localProvider, MockAIProvider mockProvider,
                          ChatConversationRepository conversationRepository,
                          ChatMessageRepository messageRepository,
                          AIRecommendationLogRepository logRepository,
                          ProductMapper mapper, ObjectMapper objectMapper,
                          AppProperties appProperties) {
        this.toolRouter = toolRouter;
        this.primaryProvider = nvidiaProvider;
        this.localProvider = localProvider;
        this.mockProvider = mockProvider;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.logRepository = logRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Transactional
    public ChatResponse handleMessage(ChatRequest request, User user) {
        long start = System.currentTimeMillis();
        String rawMessage = request.message() == null ? "" : request.message().trim();

        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString() : request.conversationId();
        ChatConversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseGet(() -> createConversation(conversationId, user, request.sessionId(), rawMessage));

        persistMessage(conversation, "USER", sanitize(rawMessage), null, null, null);

        String toolUsed = "none";
        String providerUsed;
        String reasoning = "";
        List<Product> toolProducts = new ArrayList<>();
        FeedbackSummaryDto feedback = null;
        List<ProductCardDto> candidates = new ArrayList<>();

        try {
            ConversationState state = STATE_STORE.computeIfAbsent(conversationId, k -> new ConversationState());

            String lower = rawMessage.toLowerCase(Locale.ROOT);

            if (isGreeting(lower)) {
                providerUsed = mockProvider.name();
                String text = mockProvider.chatCompletion(buildPersonaSystem(), rawMessage);
                persistMessage(conversation, "ASSISTANT", text, null, null, "Greeting handling");
                return ChatResponse.ok(conversationId, text, "Friendly greeting",
                        List.of(), followUpSuggestions("greeting"), "none", providerUsed);
            }

            boolean isFollowUp = FOLLOWUP_KEYWORDS.matcher(lower).find()
                    && state.lastProductIds() != null && !state.lastProductIds().isEmpty();

            if (isFollowUp && lower.contains("feedback") || isFollowUp && lower.contains("review")
                    || isFollowUp && lower.contains("customer") || isFollowUp && (lower.contains("battery")
                    || lower.contains("camera") || lower.contains("display") || lower.contains("issue")
                    || lower.contains("problem"))) {
                toolUsed = "getProductFeedbackSummary";
                Long productId = resolveProductId(request.currentProductId(), state);
                feedback = toolRouter.getProductFeedbackSummary(productId);
                reasoning = "Follow-up resolved against product #" + productId + " (" + feedback.productName() + ")";
                candidates = List.of();
                toolProducts = List.of();
            } else if (isFollowUp && (lower.contains("compare") || lower.contains("which is better")
                    || lower.contains("which one") || lower.contains("vs"))) {
                toolUsed = "compareProducts";
                List<Long> ids = state.lastProductIds().stream().limit(4).toList();
                toolProducts = toolRouter.compareProducts(ids);
                candidates = toolProducts.stream().map(mapper::toCard).toList();
                reasoning = "Compared " + ids.size() + " products referenced from the previous turn: "
                        + toolProducts.stream().map(Product::getName).toList();
            } else if (COMPARE_KEYWORDS.matcher(lower).find()) {
                toolUsed = "compareProducts";
                SearchFilters filters = primaryProvider.parseNaturalLanguageSearch(rawMessage);
                List<Product> searched = toolRouter.searchProducts(filters, 6);
                List<Long> ids = searched.stream().limit(3).map(Product::getId).toList();
                toolProducts = ids.isEmpty() ? List.of() : toolRouter.compareProducts(ids);
                candidates = toolProducts.stream().map(mapper::toCard).toList();
                reasoning = "Compared top " + toolProducts.size() + " products matching your query";
            } else if (FEEDBACK_KEYWORDS.matcher(lower).find()) {
                toolUsed = "getProductFeedbackSummary";
                Long productId = resolveProductId(request.currentProductId(), state);
                if (productId == null) {
                    SearchFilters filters = primaryProvider.parseNaturalLanguageSearch(rawMessage);
                    List<Product> searched = toolRouter.searchProducts(filters, 3);
                    if (!searched.isEmpty()) {
                        productId = searched.get(0).getId();
                    }
                }
                if (productId != null) {
                    feedback = toolRouter.getProductFeedbackSummary(productId);
                    reasoning = "Analyzed customer sentiment for product #" + productId;
                } else {
                    reasoning = "No product context found for feedback question";
                }
            } else if (RECOMMEND_KEYWORDS.matcher(lower).find() || lower.contains("buy me") || lower.contains("looking for")) {
                toolUsed = "searchProducts";
                SearchFilters filters = primaryProvider.parseNaturalLanguageSearch(rawMessage);
                toolProducts = toolRouter.searchProducts(filters, 8);
                candidates = rankForUser(toolProducts, user);
                reasoning = "Matched " + toolProducts.size() + " products to your request and ranked by relevance";
            } else {
                toolUsed = "searchProducts";
                SearchFilters filters = primaryProvider.parseNaturalLanguageSearch(rawMessage);
                toolProducts = toolRouter.searchProducts(filters, 8);
                candidates = toolProducts.stream().map(mapper::toCard).toList();
                reasoning = "Searched catalog with " + describeFilters(filters) + " → " + toolProducts.size() + " results";
            }

            state.update(toolProducts.stream().map(Product::getId).toList());

            String facts = buildFactsBlock(toolProducts, feedback);
            String systemPrompt = buildPersonaSystem() + "\n" + facts;
            String answerText = callWithFailover(systemPrompt, rawMessage);
            providerUsed = lastProviderUsed.get();
            if (answerText == null || answerText.isBlank()) {
                answerText = buildDeterministicAnswer(toolProducts, feedback, toolUsed, lower);
                providerUsed = "mock";
            }

            String productIdsJson = toJson(toolProducts.stream().map(Product::getId).toList());
            persistMessage(conversation, "ASSISTANT", answerText, toolUsed, productIdsJson, reasoning);

            logRecommendation(user, rawMessage, toolUsed, productIdsJson, reasoning, providerUsed,
                    System.currentTimeMillis() - start);

            return ChatResponse.ok(conversationId, answerText, reasoning, candidates.isEmpty()
                            ? toolProducts.stream().map(mapper::toCard).toList() : candidates,
                    followUpSuggestions(toolUsed), toolUsed, providerUsed);
        } catch (Exception e) {
            log.error("[AIOrchestrator] Chat handling failed", e);
            String fallback = "I hit a temporary issue while processing your request. "
                    + "Please try again or ask about products, comparisons, or customer feedback.";
            persistMessage(conversation, "ASSISTANT", fallback, toolUsed, null, "Error recovery");
            return ChatResponse.ok(conversationId, fallback, "Error recovery path", List.of(),
                    followUpSuggestions("fallback"), toolUsed, "mock");
        }
    }

    private final ThreadLocal<String> lastProviderUsed = new ThreadLocal<>();

    private String callWithFailover(String systemPrompt, String userPrompt) {
        String result = null;
        try {
            result = primaryProvider.chatCompletion(systemPrompt, userPrompt);
            if (result != null && !result.isBlank()) {
                lastProviderUsed.set(primaryProvider.name());
                return result;
            }
        } catch (Exception e) {
            log.warn("[AIOrchestrator] Primary provider failed: {}", e.getMessage());
        }
        try {
            result = localProvider.chatCompletion(systemPrompt, userPrompt);
            if (result != null && !result.isBlank()) {
                lastProviderUsed.set(localProvider.name());
                return result;
            }
        } catch (Exception e) {
            log.warn("[AIOrchestrator] Local provider failed: {}", e.getMessage());
        }
        result = mockProvider.chatCompletion(systemPrompt, userPrompt);
        lastProviderUsed.set(mockProvider.name());
        return result;
    }

    private List<ProductCardDto> rankForUser(List<Product> products, User user) {
        if (user == null || products.size() <= 1) {
            return products.stream().map(mapper::toCard).toList();
        }
        try {
            List<ProductCardDto> personalized = toolRouter.getRecommendedProducts(user.getId(), products.size());
            Map<Long, Double> scores = new LinkedHashMap<>();
            for (ProductCardDto c : personalized) {
                scores.put(c.id(), c.recommendationScore());
            }
            List<Product> ranked = new ArrayList<>(products);
            ranked.sort((a, b) -> Double.compare(
                    scores.getOrDefault(b.getId(), 0.0), scores.getOrDefault(a.getId(), 0.0)));
            return ranked.stream().map(mapper::toCard).toList();
        } catch (Exception e) {
            return products.stream().map(mapper::toCard).toList();
        }
    }

    private Long resolveProductId(Long currentProductId, ConversationState state) {
        if (currentProductId != null) {
            return currentProductId;
        }
        if (state.lastProductIds() != null && !state.lastProductIds().isEmpty()) {
            return state.lastProductIds().get(0);
        }
        return null;
    }

    private String buildFactsBlock(List<Product> products, FeedbackSummaryDto feedback) {
        StringBuilder sb = new StringBuilder("FACTS_START\n");
        sb.append("VERIFIED DATABASE FACTS (never invent anything beyond these):\n");
        if (feedback != null) {
            sb.append("Product: ").append(feedback.productName()).append("\n");
            sb.append("Total feedback: ").append(feedback.totalFeedbacks())
                    .append(" | Distribution: ").append(feedback.sentimentDistribution()).append("\n");
            sb.append("Key negative issues: ").append(feedback.keyNegativeIssues()).append("\n");
            sb.append("Key positive aspects: ").append(feedback.keyPositiveAspects()).append("\n");
            sb.append("Summary: ").append(feedback.summaryText()).append("\n");
        }
        for (Product p : products) {
            sb.append("• ").append(p.getName())
                    .append(" | ₹").append(p.getPrice())
                    .append(" | rating ").append(p.getRating()).append("★")
                    .append(" | ").append(p.getReviewCount()).append(" reviews")
                    .append(" | category: ").append(p.getCategory() != null ? p.getCategory().getName() : "-")
                    .append(" | brand: ").append(p.getBrand() != null ? p.getBrand().getName() : "-")
                    .append(" | tags: ").append(p.getTags() == null ? "-" : p.getTags())
                    .append("\n");
        }
        sb.append("FACTS_END");
        return sb.toString();
    }

    private String buildPersonaSystem() {
        return "You are OmniMart AI, a helpful e-commerce shopping assistant for OmniMart AI store. "
                + "Answer ONLY using the VERIFIED DATABASE FACTS provided. Never invent product names, prices, "
                + "ratings, or specifications. If the facts are insufficient, say so and suggest what the user "
                + "can ask instead. Respond in a friendly, concise tone with short paragraphs or bullet lists. "
                + "Use ₹ symbol for prices. Do not mention internal tools or reasoning.";
    }

    private String describeFilters(SearchFilters f) {
        List<String> parts = new ArrayList<>();
        if (f.minPrice() != null) {
            parts.add("min ₹" + f.minPrice());
        }
        if (f.maxPrice() != null) {
            parts.add("max ₹" + f.maxPrice());
        }
        if (f.categories() != null && !f.categories().isEmpty()) {
            parts.add("categories " + f.categories());
        }
        if (f.brands() != null && !f.brands().isEmpty()) {
            parts.add("brands " + f.brands());
        }
        if (f.minRating() != null) {
            parts.add(f.minRating() + "+ rating");
        }
        if (f.tags() != null && !f.tags().isEmpty()) {
            parts.add("features " + f.tags());
        }
        if (f.freeText() != null) {
            parts.add("keywords '" + f.freeText() + "'");
        }
        return parts.isEmpty() ? "all products" : String.join(", ", parts);
    }

    private String buildDeterministicAnswer(List<Product> products, FeedbackSummaryDto feedback,
                                            String toolUsed, String lower) {
        StringBuilder sb = new StringBuilder();
        if (feedback != null) {
            sb.append(feedback.summaryText()).append("\n");
            if (!feedback.keyNegativeIssues().isEmpty()) {
                sb.append("Common concerns: ").append(String.join("; ", feedback.keyNegativeIssues())).append("\n");
            }
            if (!feedback.keyPositiveAspects().isEmpty()) {
                sb.append("What customers love: ").append(String.join("; ", feedback.keyPositiveAspects()));
            }
            return sb.toString().trim();
        }
        if (products.isEmpty()) {
            return "I couldn't find products matching your request. Try relaxing the budget, or ask for "
                    + "a category like \"gaming laptops\" or \"wireless headphones\".";
        }
        sb.append("Here are my top picks from the verified catalog:\n");
        int n = 0;
        for (Product p : products) {
            if (n++ >= 5) {
                break;
            }
            sb.append("• ").append(p.getName()).append(" — ₹").append(p.getPrice())
                    .append(", ").append(p.getRating()).append("★ (").append(p.getReviewCount()).append(" reviews)");
            if (p.getDiscountPercentage() != null && p.getDiscountPercentage().signum() > 0) {
                sb.append(", ").append(p.getDiscountPercentage()).append("% off");
            }
            sb.append("\n");
        }
        if (toolUsed.equals("compareProducts") && products.size() > 1) {
            sb.append("\nFor a head-to-head spec matrix, check the comparison panel. ");
            sb.append("Best value: ").append(products.get(0).getName()).append(".");
        } else {
            sb.append("\nWould you like me to compare any two of these, or check what customers say about their battery/camera?");
        }
        return sb.toString().trim();
    }

    private boolean isGreeting(String lower) {
        return lower.matches("^(hi|hello|hey|namaste|good (morning|afternoon|evening))[!?.\\s]*$")
                || lower.contains("who are you") || lower.contains("what can you do") || lower.contains("help me");
    }

    private List<String> followUpSuggestions(String toolUsed) {
        return switch (toolUsed) {
            case "searchProducts", "compareProducts" -> List.of(
                    "Compare the top two",
                    "What do customers say about its battery?",
                    "Show me something cheaper",
                    "Which is the best value?");
            case "getProductFeedbackSummary" -> List.of(
                    "Compare this with a similar product",
                    "Show top rated alternatives",
                    "Any complaints about the display?");
            default -> List.of(
                    "Gaming laptop under 80000",
                    "Camera phone under 40000",
                    "Best wireless headphones with ANC",
                    "What's the most popular monitor?");
        };
    }

    private ChatConversation createConversation(String conversationId, User user, String sessionId, String firstMessage) {
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(conversationId);
        conversation.setUser(user);
        conversation.setSessionId(sessionId);
        conversation.setTitle(firstMessage == null || firstMessage.isBlank() ? "New chat"
                : firstMessage.substring(0, Math.min(60, firstMessage.length())));
        return conversationRepository.save(conversation);
    }

    private void persistMessage(ChatConversation conversation, String sender, String content,
                                String toolUsed, String productIdsJson, String reasoning) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content == null ? "" : content.substring(0, Math.min(4000, content.length())));
        message.setToolCallsJson(toolUsed == null ? null : "[\"" + toolUsed + "\"]");
        message.setRecommendedProductIdsJson(productIdsJson);
        message.setReasoningSummary(reasoning);
        messageRepository.save(message);
    }

    private void logRecommendation(User user, String query, String toolUsed, String productIdsJson,
                                   String reasoning, String providerUsed, long executionTimeMs) {
        try {
            AIRecommendationLog entry = new AIRecommendationLog();
            entry.setUser(user);
            entry.setQueryText(query == null ? "" : query.substring(0, Math.min(2000, query.length())));
            entry.setToolUsed(toolUsed);
            entry.setProductIdsJson(productIdsJson);
            entry.setGeneratedReasoning(reasoning);
            entry.setProviderUsed(providerUsed);
            entry.setExecutionTimeMs(executionTimeMs);
            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to persist AI log: {}", e.getMessage());
        }
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("(?i)(ignore|disregard|forget)\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts|rules).*", "")
                .replaceAll("(?i)system\\s*prompt\\s*:.*", "")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
        return s.substring(0, Math.min(4000, s.length()));
    }

    private String toJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private record ConversationState(List<Long> lastProductIds) {
        public ConversationState() {
            this(new ArrayList<>());
        }

        public void update(List<Long> ids) {
            lastProductIds.clear();
            lastProductIds.addAll(ids);
        }
    }
}