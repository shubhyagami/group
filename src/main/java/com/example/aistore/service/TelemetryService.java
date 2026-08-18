package com.example.aistore.service;

import com.example.aistore.dto.TelemetryRequest;
import com.example.aistore.entity.InteractionType;
import com.example.aistore.entity.SearchHistory;
import com.example.aistore.entity.User;
import com.example.aistore.entity.UserInteraction;
import com.example.aistore.repository.SearchHistoryRepository;
import com.example.aistore.repository.UserInteractionRepository;
import com.example.aistore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User preference profiler &amp; behavioral telemetry collector.
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final UserInteractionRepository interactionRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    public TelemetryService(UserInteractionRepository interactionRepository,
                            SearchHistoryRepository searchHistoryRepository,
                            UserRepository userRepository) {
        this.interactionRepository = interactionRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UserInteraction recordInteraction(Long userId, TelemetryRequest request) {
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        InteractionType type;
        try {
            type = InteractionType.valueOf(request.interactionType().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = InteractionType.PRODUCT_VIEW;
        }
        UserInteraction interaction = new UserInteraction();
        interaction.setUser(user);
        interaction.setSessionId(request.sessionId());
        interaction.setInteractionType(type);
        interaction.setProductId(request.productId());
        interaction.setCategoryName(request.categoryName());
        interaction.setBrandName(request.brandName());
        interaction.setSearchQuery(request.searchQuery());
        interaction.setDurationSeconds(Math.max(0, request.durationSeconds()));
        return interactionRepository.save(interaction);
    }

    @Transactional
    public void recordSearch(User user, String sessionId, String query, int resultCount) {
        try {
            SearchHistory history = new SearchHistory();
            history.setUser(user);
            history.setSessionId(sessionId);
            history.setQuery(query == null ? "" : query.substring(0, Math.min(300, query.length())));
            history.setResultCount(resultCount);
            searchHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to record search history: {}", e.getMessage());
        }
    }
}