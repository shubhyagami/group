package com.example.aistore.ai;

import com.example.aistore.entity.Brand;
import com.example.aistore.entity.Category;
import com.example.aistore.repository.BrandRepository;
import com.example.aistore.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fully deterministic, zero-dependency offline rule &amp; NLP spec engine used as the
 * guaranteed-available fallback for every AI capability.
 */
@Component
public class MockAIProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(MockAIProvider.class);

    private static final Pattern UNDER_BUDGET = Pattern.compile(
            "(?:under|below|less\\s*than|within|upto|up\\s*to|max(?:imum)?(?:\\s*budget)?|bgt|under)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d+(?:\\.\\d+)?)\\s*(k)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AROUND_BUDGET = Pattern.compile(
            "(?:around|approx(?:imately)?|about)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d+(?:\\.\\d+)?)\\s*(k)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BETWEEN_BUDGET = Pattern.compile(
            "(?:between|from)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d+(?:\\.\\d+)?)\\s*(k)?\\s*(?:to|and|-)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d+(?:\\.\\d+)?)\\s*(k)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OVER_BUDGET = Pattern.compile(
            "(?:above|over|more\\s*than|at\\s*least|min(?:imum)?)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d+(?:\\.\\d+)?)\\s*(k)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RATING_PATTERN = Pattern.compile(
            "(\\d(?:\\.\\d)?)\\s*(?:\\+|plus)?\\s*star", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> CATEGORY_SYNONYMS = Map.ofEntries(
            Map.entry("camera phone", "Smartphones"),
            Map.entry("smartphone", "Smartphones"),
            Map.entry("phone", "Smartphones"),
            Map.entry("mobile", "Smartphones"),
            Map.entry("laptop", "Laptops"),
            Map.entry("ultrabook", "Laptops"),
            Map.entry("notebook", "Laptops"),
            Map.entry("earbuds", "Headphones"),
            Map.entry("earphones", "Headphones"),
            Map.entry("headset", "Headphones"),
            Map.entry("headphone", "Headphones"),
            Map.entry("monitor", "Monitors"),
            Map.entry("console", "Gaming"),
            Map.entry("gaming laptop", "Laptops"),
            Map.entry("mirrorless", "Cameras"),
            Map.entry("camera", "Cameras"),
            Map.entry("webcam", "Accessories"),
            Map.entry("mouse", "Accessories"),
            Map.entry("keyboard", "Accessories"),
            Map.entry("charger", "Accessories"));

    private static final Map<String, String> FEATURE_TAGS = Map.ofEntries(
            Map.entry("gaming", "gaming"), Map.entry("game", "gaming"),
            Map.entry("flagship", "flagship"), Map.entry("premium", "flagship"),
            Map.entry("oled", "oled"), Map.entry("amoled", "amoled"),
            Map.entry("5g", "5g"), Map.entry("5g phone", "5g"),
            Map.entry("anc", "anc"), Map.entry("noise cancelling", "anc"), Map.entry("noise-cancelling", "anc"),
            Map.entry("bluetooth", "bluetooth"), Map.entry("wireless", "wireless"),
            Map.entry("4k", "4k"), Map.entry("hdr", "hdr"),
            Map.entry("fast charging", "fast-charging"), Map.entry("fastcharge", "fast-charging"),
            Map.entry("battery", "battery"), Map.entry("long battery", "battery"),
            Map.entry("camera", "camera"), Map.entry("camera phone", "camera"),
            Map.entry("ssd", "ssd"), Map.entry("rgb", "rgb"),
            Map.entry("lightweight", "lightweight"), Map.entry("light weight", "lightweight"),
            Map.entry("budget", "budget"), Map.entry("affordable", "budget"),
            Map.entry("bestseller", "bestseller"), Map.entry("top rated", "top-rated"),
            Map.entry("foldable", "foldable"), Map.entry("tablet", "tablet"),
            Map.entry("macbook", "macbook"), Map.entry("iphone", "iphone"),
            Map.entry("samsung", "samsung"), Map.entry("apple", "apple"),
            Map.entry("sony", "sony"), Map.entry("oneplus", "oneplus"));

    private static final Map<String, List<String>> SENTIMENT_POSITIVE_WORDS = Map.ofEntries(
            Map.entry("great", List.of("great")), Map.entry("amazing", List.of("amazing")),
            Map.entry("excellent", List.of("excellent", "outstanding")),
            Map.entry("love", List.of("love", "loving", "loved")),
            Map.entry("best", List.of("best")), Map.entry("good", List.of("good", "nice", "solid", "great value")),
            Map.entry("worth", List.of("worth", "value for money", "vfm")),
            Map.entry("smooth", List.of("smooth", "fast", "snappy", "fluid")),
            Map.entry("crisp", List.of("crisp", "vibrant", "sharp", "colorful")),
            Map.entry("recommend", List.of("recommend", "recommended")));

    private static final List<String> NEGATIVE_WORDS = List.of(
            "bad", "worst", "awful", "terrible", "poor", "disappoint", "disappointed", "not worth",
            "waste", "battery drain", "heating", "overheating", "throttle", "lag", "stutter",
            "issue", "problem", "complaint", "cracked", "faulty", "defective", "refund", "broken",
            "slow", "boring", "heavy", "bulky", "noise", "hissing", "average build");

    private static final Map<String, List<String>> EMOTION_KEYWORDS = Map.ofEntries(
            Map.entry("Delighted", List.of("love", "amazing", "outstanding", "best ever", "perfect", "incredible", "wow", "excellent")),
            Map.entry("Satisfied", List.of("good", "nice", "solid", "smooth", "works well", "happy", "satisfied", "worth it", "recommend")),
            Map.entry("Frustrated", List.of("bad", "worst", "awful", "terrible", "frustrat", "battery drain", "heating", "overheat", "lag", "crash", "refund")),
            Map.entry("Disappointed", List.of("disappoint", "expected more", "not worth", "overpriced", "waste of money", "below expectation")));

    private static final Map<String, List<String>> TOPIC_KEYWORDS = Map.ofEntries(
            Map.entry("Battery", List.of("battery", "charge", "charging", "drain", "standby", "power", "backup", "fast charge")),
            Map.entry("Display", List.of("display", "screen", "amoled", "oled", "refresh rate", "panel", "brightness", "bezels", "lcd", "hdr")),
            Map.entry("Camera", List.of("camera", "photo", "photos", "pictures", "zoom", "lens", "video", "selfie", "night mode", "portrait")),
            Map.entry("Performance", List.of("performance", "speed", "lag", "processor", "chip", "smooth", "gaming", "heating", "thermal", "ram", "benchmark", "stutter")),
            Map.entry("Delivery", List.of("delivery", "shipping", "packaging", "arrived", "dispatch", "delivered", "courier", "package")),
            Map.entry("Audio", List.of("sound", "audio", "speaker", "bass", "headphone", "earbud", "anc", "noise cancelling", "mic", "microphone")),
            Map.entry("Build Quality", List.of("build", "premium", "plastic", "hinge", "sturdy", "feel", "material", "durable", "weight")));

    private static final List<String> NEGATIVE_ISSUE_PHRASES = List.of(
            "battery drain under gaming", "battery drains fast", "heats up", "heating issue", "thermal throttling",
            "overheats", "average camera", "camera is average", "low light photos are bad", "display flickers",
            "green tint", "dead pixel", "screen has", "ghosting", "sound is average", "bass is weak",
            "anc is average", "noise cancellation is weak", "delivery was delayed", "late delivery",
            "packaging was damaged", "plastic build", "build quality is average", "creaking hinge",
            "software bugs", "laggy", "stutters", "slow charging", "charger heats", "fingerprint magnet",
            "bloatware", "advertisements", "ads in", "poor signal", "call quality is poor", "speaker is weak");

    private static final List<String> POSITIVE_ASPECT_PHRASES = List.of(
            "vibrant 120hz amoled", "vibrant display", "crisp display", "bright screen", "super fast 1-day delivery",
            "fast delivery", "great battery life", "all-day battery", "two-day battery", "excellent camera",
            "amazing camera", "camera is great", "insane camera", "smooth performance", "buttery smooth",
            "no lag", "premium build", "sturdy build", "great sound", "punchy bass", "excellent anc",
            "great anc", "best in class", "value for money", "great value", "fast charging", "superb speakers");

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public MockAIProvider(CategoryRepository categoryRepository, BrandRepository brandRepository) {
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    public String name() {
        return "mock";
    }

    // ========================================================================
    // NATURAL LANGUAGE SEARCH PARSING
    // ========================================================================

    @Override
    public SearchFilters parseNaturalLanguageSearch(String query) {
        if (query == null || query.isBlank()) {
            return SearchFilters.empty();
        }
        String q = query.toLowerCase(Locale.ROOT).trim();

        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;

        Matcher between = BETWEEN_BUDGET.matcher(q);
        if (between.find()) {
            minPrice = parseMoney(between.group(1), between.group(2));
            maxPrice = parseMoney(between.group(3), between.group(4));
        } else {
            Matcher under = UNDER_BUDGET.matcher(q);
            if (under.find()) {
                maxPrice = parseMoney(under.group(1), under.group(2));
            } else {
                Matcher around = AROUND_BUDGET.matcher(q);
                if (around.find()) {
                    BigDecimal center = parseMoney(around.group(1), around.group(2));
                    if (center != null) {
                        minPrice = center.multiply(BigDecimal.valueOf(0.85));
                        maxPrice = center.multiply(BigDecimal.valueOf(1.15));
                    }
                } else {
                    Matcher over = OVER_BUDGET.matcher(q);
                    if (over.find()) {
                        minPrice = parseMoney(over.group(1), over.group(2));
                    }
                }
            }
        }

        Set<String> categories = new LinkedHashSet<>();
        for (Category c : categoryRepository.findAll()) {
            if (q.contains(c.getName().toLowerCase(Locale.ROOT))) {
                categories.add(c.getName());
            }
        }
        for (Map.Entry<String, String> e : CATEGORY_SYNONYMS.entrySet()) {
            if (q.contains(e.getKey())) {
                categories.add(e.getValue());
            }
        }

        Set<String> brands = new LinkedHashSet<>();
        for (Brand b : brandRepository.findAll()) {
            if (q.contains(b.getName().toLowerCase(Locale.ROOT))) {
                brands.add(b.getName());
            }
        }

        Double minRating = null;
        Matcher rating = RATING_PATTERN.matcher(q);
        if (rating.find()) {
            minRating = Double.parseDouble(rating.group(1));
        } else if (q.contains("top rated") || q.contains("highly rated") || q.contains("best rated")) {
            minRating = 4.0;
        }

        Set<String> tags = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : FEATURE_TAGS.entrySet()) {
            if (q.contains(e.getKey())) {
                tags.add(e.getValue());
            }
        }

        String freeText = deriveFreeText(q, categories, brands);
        return new SearchFilters(minPrice, maxPrice, categories, brands, minRating, tags, freeText);
    }

    private String deriveFreeText(String q, Set<String> categories, Set<String> brands) {
        String text = q;
        for (String c : categories) {
            text = text.replace(c.toLowerCase(Locale.ROOT), " ");
        }
        for (String b : brands) {
            text = text.replace(b.toLowerCase(Locale.ROOT), " ");
        }
        text = text.replaceAll("(under|below|less than|within|upto|up to|around|between|from|above|over|more than|at least|best|good|cheap|affordable|buy|show|me|for|with|and|under|recommend|suggest|compare|the|top|two|three|first|second|third|please|want|need|looking|find|get|i|a|an)\\s*", " ")
                .replaceAll("\\d+(\\.\\d+)?\\s*(k|rs|inr|₹)?", " ")
                .replaceAll("\\s*[+|]?\\s*star\\s*", " ")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ").trim();
        if (text.length() <= 2) {
            return null;
        }
        return text;
    }

    private BigDecimal parseMoney(String amount, String kSuffix) {
        try {
            BigDecimal value = new BigDecimal(amount);
            if (kSuffix != null && !kSuffix.isBlank()) {
                value = value.multiply(BigDecimal.valueOf(1000));
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========================================================================
    // CUSTOMER FEEDBACK SENTIMENT & EMOTION NLP
    // ========================================================================

    @Override
    public FeedbackAnalysis analyzeCustomerFeedback(String text, int rating, String title) {
        String combined = ((title == null ? "" : title) + " " + (text == null ? "" : text)).toLowerCase(Locale.ROOT);

        String sentiment;
        if (rating >= 4) {
            sentiment = "Positive";
        } else if (rating <= 2) {
            sentiment = "Negative";
        } else {
            boolean hasPositive = SENTIMENT_POSITIVE_WORDS.values().stream()
                    .anyMatch(kws -> kws.stream().anyMatch(combined::contains));
            boolean hasNegative = NEGATIVE_WORDS.stream().anyMatch(combined::contains);
            sentiment = hasPositive && hasNegative ? "Mixed" : (hasPositive ? "Positive" : (hasNegative ? "Negative" : "Neutral"));
        }

        String emotion = "Neutral";
        for (Map.Entry<String, List<String>> e : EMOTION_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (combined.contains(kw)) {
                    emotion = e.getKey();
                    break;
                }
            }
            if (!"Neutral".equals(emotion)) {
                break;
            }
        }

        String primaryTopic = "General";
        Map<String, Integer> topicHits = new java.util.HashMap<>();
        for (Map.Entry<String, List<String>> e : TOPIC_KEYWORDS.entrySet()) {
            int hits = 0;
            for (String kw : e.getValue()) {
                if (combined.contains(kw)) {
                    hits++;
                }
            }
            if (hits > 0) {
                topicHits.put(e.getKey(), hits);
            }
        }
        if (!topicHits.isEmpty()) {
            primaryTopic = topicHits.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("General");
        }

        List<String> issues = new ArrayList<>();
        for (String phrase : NEGATIVE_ISSUE_PHRASES) {
            if (combined.contains(phrase)) {
                issues.add(phrase);
            }
        }

        List<String> positives = new ArrayList<>();
        for (String phrase : POSITIVE_ASPECT_PHRASES) {
            if (combined.contains(phrase)) {
                positives.add(phrase);
            }
        }

        if (issues.isEmpty() && "Negative".equals(sentiment)) {
            for (String kw : NEGATIVE_WORDS) {
                if (combined.contains(kw)) {
                    issues.add(kw + " (general concern)");
                    break;
                }
            }
        }
        if (positives.isEmpty() && "Positive".equals(sentiment)) {
            for (List<String> kws : SENTIMENT_POSITIVE_WORDS.values()) {
                for (String kw : kws) {
                    if (combined.contains(kw)) {
                        positives.add(kw);
                        break;
                    }
                }
                if (!positives.isEmpty()) {
                    break;
                }
            }
        }

        double confidence = Math.min(0.99, 0.6 + 0.08 * Math.abs(rating - 3) + (issues.size() + positives.size()) * 0.02);
        return new FeedbackAnalysis(sentiment, emotion, primaryTopic, issues, positives, confidence);
    }

    // ========================================================================
    // ADMIN BUSINESS INTELLIGENCE
    // ========================================================================

    @Override
    public String answerAdminQuery(String question, String contextJson) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();

        if (q.contains("revenue") || q.contains("sales") || q.contains("earn") || q.contains("money")) {
            sb.append("Revenue insight: ").append(extract(contextJson, "totalRevenue"))
                    .append(" total revenue (delivered: ").append(extract(contextJson, "deliveredRevenue"))
                    .append("), average order value ").append(extract(contextJson, "avgOrderValue"))
                    .append(". Revenue per customer is healthy when average order value stays above product costs.\n");
        }
        if (q.contains("order") || q.contains("sell")) {
            sb.append("Order insight: ").append(extract(contextJson, "orderCount"))
                    .append(" orders placed across ").append(extract(contextJson, "userCount"))
                    .append(" registered customers. Top sellers: ").append(extract(contextJson, "topSellingProducts"))
                    .append(".\n");
        }
        if (q.contains("sentiment") || q.contains("review") || q.contains("feedback") || q.contains("customer say")) {
            sb.append("Sentiment insight: distribution is ").append(extract(contextJson, "sentimentDistribution"))
                    .append(". Most common negative topic: ").append(extract(contextJson, "negativeIssuesByTopic"))
                    .append(".\n");
        }
        if (q.contains("churn") || q.contains("risk") || q.contains("at risk")) {
            sb.append("Churn insight: ").append(extract(contextJson, "churnRiskSignals"))
                    .append(" customers show churn-risk signals (recent cancellations or heavy negative feedback).\n");
        }
        if (q.contains("stock") || q.contains("inventory") || q.contains("low stock")) {
            sb.append("Inventory insight: ").append(extract(contextJson, "lowStockProducts"))
                    .append(" products are below restock thresholds.\n");
        }
        if (sb.isEmpty()) {
            sb.append("Summary: store is generating ").append(extract(contextJson, "totalRevenue"))
                    .append(" revenue from ").append(extract(contextJson, "orderCount"))
                    .append(" orders. Sentiment ").append(extract(contextJson, "sentimentDistribution"))
                    .append(". Priority action: resolve the top negative topic to protect retention.\n");
        }
        return sb.toString().trim();
    }

    private String extract(String json, String key) {
        if (json == null || json.isBlank()) {
            return "n/a";
        }
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return "n/a";
        }
        int colon = json.indexOf(':', idx);
        if (colon < 0) {
            return "n/a";
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return "n/a";
        }
        char c = json.charAt(start);
        int end;
        if (c == '"') {
            end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start + 1, end) : json.substring(start);
        }
        end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        String raw = json.substring(start, end).trim();
        return raw.length() > 160 ? raw.substring(0, 157) + "..." : raw;
    }

    // ========================================================================
    // DETERMINISTIC CHAT COMPLETION
    // ========================================================================

    @Override
    public String chatCompletion(String systemPrompt, String userPrompt) {
        String user = userPrompt == null ? "" : userPrompt.toLowerCase(Locale.ROOT);
        StringBuilder answer = new StringBuilder();

        if (user.contains("hello") || user.contains("hi ") || user.equals("hi") || user.contains("hey")) {
            answer.append("Hello! I'm your OmniMart AI shopping assistant. I can help you find products, ")
                    .append("compare specs, check customer reviews, and recommend the best tech within your budget. ")
                    .append("Try asking things like \"gaming laptop under 80000\" or \"compare the top two\".");
            return answer.toString();
        }
        if (user.contains("thank")) {
            return "You're welcome! Happy shopping at OmniMart AI. Let me know if you need anything else.";
        }

        List<String> facts = extractFacts(systemPrompt);
        if (facts.isEmpty()) {
            boolean feedbackIntent = user.contains("review") || user.contains("feedback")
                    || user.contains("complaint") || user.contains("issues")
                    || user.contains("what do customers say") || user.contains("sentiment");
            if (feedbackIntent) {
                return "I couldn't find customer feedback for that product yet. Try asking about a product you've "
                        + "already seen, or say \"compare the top two\" after a search so I can pull verified review analytics.";
            }
            if (user.contains("compare")) {
                return "I couldn't gather enough verified comparison data for that request. Try asking something like "
                        + "\"compare gaming laptops under 80000\" or \"compare the top two\" after a search.";
            }
            return "I couldn't find products matching that exact request in the live catalog. Try adjusting the budget "
                    + "or asking for a category such as \"gaming laptops under 80000\", \"camera phone under 40000\", "
                    + "or \"sony headphones with anc\".";
        }

        boolean feedbackFacts = facts.stream().anyMatch(f -> f.startsWith("Total feedback:"));
        answer.append(feedbackFacts
                ? "Here's what verified customer feedback says:\n"
                : "Based on verified catalog data, here are my top picks:\n");
        int shown = 0;
        for (String fact : facts) {
            if (shown++ >= 5) {
                break;
            }
            answer.append("• ").append(fact).append("\n");
        }
        if (user.contains("compare")) {
            answer.append("\nVerdict: pick based on your priority — best price for value, or best rating for confidence.");
        } else if (feedbackFacts) {
            answer.append("\nTip: focus on the recurring negative issues above before deciding.");
        } else if (user.contains("review") || user.contains("feedback") || user.contains("battery")) {
            answer.append("\nTip: check the feedback summary for recurring issues before you buy.");
        } else {
            answer.append("\nAll recommendations are ranked by the hybrid scoring model: preferences, behavior, content match, rating and popularity.");
        }
        return answer.toString().trim();
    }

    private List<String> extractFacts(String systemPrompt) {
        List<String> facts = new ArrayList<>();
        if (systemPrompt == null) {
            return facts;
        }
        int marker = systemPrompt.indexOf("FACTS_START");
        int endMarker = systemPrompt.indexOf("FACTS_END");
        if (marker >= 0 && endMarker > marker) {
            String block = systemPrompt.substring(marker + "FACTS_START".length(), endMarker);
            for (String line : block.split("\n")) {
                String l = line.trim();
                if (!l.isEmpty() && (l.contains("₹") || l.contains("Rs") || l.contains("rating") || l.contains("★")
                        || l.startsWith("Product:") || l.startsWith("Total feedback:")
                        || l.startsWith("Key ") || l.startsWith("Summary:"))) {
                    facts.add(l);
                }
            }
        }
        if (facts.isEmpty() && systemPrompt.contains("•")) {
            for (String line : systemPrompt.split("\n")) {
                String l = line.trim();
                if (l.startsWith("•")) {
                    facts.add(l.substring(1).trim());
                }
            }
        }
        return facts;
    }
}