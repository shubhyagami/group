package com.example.aistore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpSendRequest(
        @NotBlank @Email String email,
        String name,
        String purpose
) {
}