package com.example.aistore.dto;

public record SearchNlRequest(
        String query,
        int limit
) {
    public SearchNlRequest {
        if (limit <= 0) {
            limit = 12;
        }
    }
}