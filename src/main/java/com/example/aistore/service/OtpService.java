package com.example.aistore.service;

import com.example.aistore.config.AppProperties;
import com.example.aistore.dto.OtpSendResponse;
import com.example.aistore.service.email.BrevoEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory concurrent OTP service with TTL expiry and failed-attempt rate limiting.
 * In dev mode ({@code app.otp.dev-mode=true}) the generated code is logged and
 * returned in {@link OtpSendResponse#devCode()} so flows can be exercised without
 * a live email provider.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final Random RANDOM = new Random();
    private static final ConcurrentHashMap<String, OtpEntry> STORE = new ConcurrentHashMap<>();

    private final AppProperties properties;
    private final BrevoEmailService emailService;

    public OtpService(AppProperties properties, BrevoEmailService emailService) {
        this.properties = properties;
        this.emailService = emailService;
    }

    public OtpSendResponse sendOtp(String email, String name, String purpose) {
        String key = normalize(email);
        if (key == null) {
            return new OtpSendResponse(false, "Valid email is required", 0, 0, null);
        }
        OtpEntry existing = STORE.get(key);
        if (existing != null && existing.isLocked()) {
            return new OtpSendResponse(false, "Too many failed attempts. Try again later.", 0, 0, null);
        }
        if (existing != null && existing.isActive()) {
            return new OtpSendResponse(true, "A valid OTP is already active. Check your inbox.",
                    existing.remainingSeconds(), properties.otp().maxAttempts(), null);
        }
        String otp = String.format("%0" + properties.otp().digits() + "d", RANDOM.nextInt((int) Math.pow(10, properties.otp().digits())));
        STORE.put(key, new OtpEntry(otp, Instant.now().plusSeconds(properties.otp().ttlMinutes() * 60L), 0, false));
        emailService.sendOtpEmail(email, name, otp, purpose == null ? "verification" : purpose);
        if (properties.otp().devMode()) {
            log.info("[OtpService] DEV MODE - OTP for {} is {}", email, otp);
        }
        return new OtpSendResponse(true, "OTP sent to " + email,
                properties.otp().ttlMinutes() * 60, properties.otp().maxAttempts(),
                properties.otp().devMode() ? otp : null);
    }

    public boolean verifyOtp(String email, String otp) {
        String key = normalize(email);
        if (key == null || otp == null || otp.isBlank()) {
            return false;
        }
        OtpEntry entry = STORE.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isLocked()) {
            return false;
        }
        if (entry.isExpired()) {
            STORE.remove(key);
            return false;
        }
        if (entry.otp().equals(otp.trim())) {
            STORE.remove(key);
            return true;
        }
        entry.recordFailedAttempt();
        if (entry.failedAttempts() >= properties.otp().maxAttempts()) {
            STORE.put(key, entry.lock());
        } else {
            STORE.put(key, entry.recordFailedAttempt());
        }
        return false;
    }

    public int remainingAttempts(String email) {
        String key = normalize(email);
        OtpEntry entry = STORE.get(key);
        if (entry == null) {
            return 0;
        }
        return Math.max(0, properties.otp().maxAttempts() - entry.failedAttempts());
    }

    private String normalize(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private record OtpEntry(String otp, Instant expiresAt, int failedAttempts, boolean locked) {

        boolean isActive() {
            return !isExpired() && !locked;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        boolean isLocked() {
            return locked;
        }

        int remainingSeconds() {
            long secs = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
            return (int) Math.max(0, secs);
        }

        OtpEntry recordFailedAttempt() {
            return new OtpEntry(otp, expiresAt, failedAttempts + 1, locked);
        }

        OtpEntry lock() {
            return new OtpEntry(otp, expiresAt, failedAttempts, true);
        }
    }
}