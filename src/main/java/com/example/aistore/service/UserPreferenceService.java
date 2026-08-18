package com.example.aistore.service;

import com.example.aistore.dto.UpdatePreferencesRequest;
import com.example.aistore.entity.User;
import com.example.aistore.entity.UserPreference;
import com.example.aistore.repository.UserPreferenceRepository;
import com.example.aistore.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public UserPreferenceService(UserPreferenceRepository preferenceRepository,
                                 UserRepository userRepository,
                                 ObjectMapper objectMapper) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UserPreference getForUser(Long userId) {
        return preferenceRepository.findByUserId(userId).orElse(null);
    }

    @Transactional
    public UserPreference update(Long userId, UpdatePreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.example.aistore.exception.ResourceNotFoundException("User", userId));
        UserPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreference created = new UserPreference();
                    created.setUser(user);
                    return created;
                });

        if (request.preferredCategories() != null) {
            preference.setPreferredCategoriesJson(toJson(request.preferredCategories()));
        }
        if (request.preferredBrands() != null) {
            preference.setPreferredBrandsJson(toJson(request.preferredBrands()));
        }
        if (request.minBudget() != null) {
            preference.setMinBudget(request.minBudget());
        }
        if (request.maxBudget() != null) {
            preference.setMaxBudget(request.maxBudget());
        }
        if (request.recommendationsEnabled() != null) {
            preference.setRecommendationsEnabled(request.recommendationsEnabled());
        }
        if (request.behaviorTrackingEnabled() != null) {
            preference.setBehaviorTrackingEnabled(request.behaviorTrackingEnabled());
        }
        return preferenceRepository.save(preference);
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> parseCategories(UserPreference preference) {
        return parse(preference != null ? preference.getPreferredCategoriesJson() : null);
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> parseBrands(UserPreference preference) {
        return parse(preference != null ? preference.getPreferredBrandsJson() : null);
    }

    private String toJson(Map<String, Integer> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Integer> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
            });
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}