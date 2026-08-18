package com.example.aistore.controller.api;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.OtpSendRequest;
import com.example.aistore.dto.OtpSendResponse;
import com.example.aistore.dto.OtpVerifyRequest;
import com.example.aistore.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/otp")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/send")
    public ApiResponse<OtpSendResponse> send(@Valid @RequestBody OtpSendRequest request) {
        return ApiResponse.ok(otpService.sendOtp(request.email(), request.name(), request.purpose()));
    }

    @PostMapping("/verify")
    public ApiResponse<Map<String, Object>> verify(@Valid @RequestBody OtpVerifyRequest request) {
        boolean valid = otpService.verifyOtp(request.email(), request.otp());
        if (valid) {
            return ApiResponse.ok("OTP verified successfully", Map.of(
                    "verified", true,
                    "attemptsRemaining", otpService.remainingAttempts(request.email())));
        }
        return ApiResponse.ok("Invalid or expired OTP", Map.of(
                "verified", false,
                "attemptsRemaining", otpService.remainingAttempts(request.email())));
    }
}