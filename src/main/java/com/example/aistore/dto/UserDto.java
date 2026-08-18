package com.example.aistore.dto;

import java.util.Set;

public record UserDto(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        boolean active,
        Set<String> roles
) {
}