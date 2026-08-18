package com.example.aistore.service.email;

import com.example.aistore.config.BrevoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Brevo (Sendinblue) transactional email service.
 * Dispatches HTML OTP verification emails and order invoices via
 * POST https://api.brevo.com/v3/smtp/email with the api-key header.
 * Never throws — all delivery failures are logged and swallowed.
 */
@Service
public class BrevoEmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    private final BrevoProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public BrevoEmailService(BrevoProperties properties, WebClient.Builder webClientBuilder,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl("https://api.brevo.com/v3").build();
        this.objectMapper = objectMapper;
    }

    @Async
    public void sendOtpEmail(String recipientEmail, String recipientName, String otpCode, String purpose) {
        String html = otpTemplate(recipientName, otpCode, purpose);
        send(recipientEmail, recipientName, "Your OmniMart AI verification code", html);
    }

    @Async
    public void sendOrderConfirmationEmail(String recipientEmail, String recipientName,
                                           String orderNumber, BigDecimal finalAmount, String itemsSummary) {
        String html = orderTemplate(recipientName, orderNumber, finalAmount, itemsSummary);
        send(recipientEmail, recipientName, "Order confirmed - " + orderNumber + " | OmniMart AI", html);
    }

    private void send(String toEmail, String toName, String subject, String htmlContent) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("[Brevo] Missing recipient email, skipping");
            return;
        }
        String apiKey = sanitizeApiKey(properties.apiKey());
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[Brevo] No API key configured. Email to {} was NOT sent (subject: {})", toEmail, subject);
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sender", Map.of("email", properties.senderEmail(), "name", properties.senderName()));
            payload.put("to", java.util.List.of(Map.of("email", toEmail, "name", toName == null ? toEmail : toName)));
            payload.put("subject", subject);
            payload.put("htmlContent", htmlContent);
            payload.put("headers", Map.of("X-OmniMart-Transaction", "true"));

            String response = webClient.post()
                    .uri(properties.baseUrl())
                    .header("api-key", apiKey)
                    .header("Accept", "application/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();
            log.info("[Brevo] Email queued to {} -> messageId={}", toEmail, response != null ? response : "n/a");
        } catch (Exception e) {
            log.warn("[Brevo] Email to {} failed: {}", toEmail, safeMessage(e));
        }
    }

    /**
     * Sanitizes the Brevo key whether passed as a standard xkeysib token or wrapped
     * in a base64-encoded envelope (as supplied in .env).
     */
    private String sanitizeApiKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String candidate = rawKey.trim();
        if (candidate.startsWith("xkeysib-")) {
            return candidate;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(candidate);
            String json = new String(decoded, StandardCharsets.UTF_8);
            if (json.contains("\"api_key\"")) {
                var node = objectMapper.readTree(json);
                String inner = node.path("api_key").asText();
                if (inner.startsWith("xkeysib-")) {
                    return inner;
                }
            }
            if (json.startsWith("xkeysib-")) {
                return json;
            }
        } catch (Exception e) {
            log.debug("[Brevo] Key is not base64-encoded, using as-is");
        }
        return candidate;
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    // ========================================================================
    // HTML TEMPLATES (dark high-contrast, responsive)
    // ========================================================================

    private String otpTemplate(String name, String otp, String purpose) {
        String purposeText = purpose == null || purpose.isBlank() ? "verification" : purpose;
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>OTP Verification</title></head>
                <body style="margin:0;padding:0;background-color:#0b1220;font-family:Segoe UI,Arial,Helvetica,sans-serif;color:#e2e8f0;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0b1220;padding:32px 16px;">
                <tr><td align="center">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%;background-color:#111a2e;border:1px solid #1e293b;border-radius:16px;overflow:hidden;">
                <tr><td style="background:linear-gradient(135deg,#2563eb,#7c3aed);padding:24px 32px;text-align:center;">
                <h1 style="margin:0;color:#ffffff;font-size:22px;letter-spacing:0.5px;">OmniMart AI</h1>
                <p style="margin:6px 0 0;color:#c7d2fe;font-size:13px;">Secure %s code</p>
                </td></tr>
                <tr><td style="padding:32px;">
                <p style="margin:0 0 8px;font-size:15px;">Hello <strong style="color:#ffffff;">%s</strong>,</p>
                <p style="margin:0 0 24px;font-size:14px;line-height:1.6;color:#94a3b8;">Use the code below to complete your <strong style="color:#ffffff;">%s</strong>. It expires in 5 minutes. Never share this code with anyone.</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                <tr><td align="center" style="padding:16px 0;">
                <span style="display:inline-block;background:#1e293b;border:1px solid #334155;border-radius:12px;padding:16px 40px;font-size:34px;font-weight:bold;letter-spacing:12px;color:#60a5fa;">%s</span>
                </td></tr>
                </table>
                <p style="margin:24px 0 0;font-size:12px;color:#64748b;text-align:center;">If you did not request this code, you can safely ignore this email.</p>
                </td></tr>
                <tr><td style="padding:16px 32px;background:#0f172a;text-align:center;font-size:12px;color:#475569;">
                &copy; 2026 OmniMart AI - Autonomous AI-Powered E-Commerce Platform
                </td></tr>
                </table>
                </td></tr></table>
                </body></html>
                """.formatted(purposeText, safe(name), purposeText, otp);
    }

    private String orderTemplate(String name, String orderNumber, BigDecimal finalAmount, String itemsSummary) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Order Confirmation</title></head>
                <body style="margin:0;padding:0;background-color:#0b1220;font-family:Segoe UI,Arial,Helvetica,sans-serif;color:#e2e8f0;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0b1220;padding:32px 16px;">
                <tr><td align="center">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%;background-color:#111a2e;border:1px solid #1e293b;border-radius:16px;overflow:hidden;">
                <tr><td style="background:linear-gradient(135deg,#059669,#2563eb);padding:24px 32px;text-align:center;">
                <h1 style="margin:0;color:#ffffff;font-size:20px;">Order Confirmed &#10003;</h1>
                <p style="margin:6px 0 0;color:#d1fae5;font-size:13px;">Order %s</p>
                </td></tr>
                <tr><td style="padding:32px;">
                <p style="margin:0 0 16px;font-size:15px;">Hi <strong style="color:#ffffff;">%s</strong>, thanks for shopping with OmniMart AI!</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#0f172a;border:1px solid #1e293b;border-radius:12px;">
                <tr><td style="padding:20px;font-size:13px;line-height:1.7;color:#cbd5e1;">
                %s
                <hr style="border:none;border-top:1px solid #1e293b;margin:14px 0;">
                <p style="margin:0;font-size:16px;">Order Total: <strong style="color:#34d399;font-size:18px;">&#8377; %s</strong></p>
                </td></tr>
                </table>
                <p style="margin:24px 0 0;font-size:12px;color:#64748b;text-align:center;">Track your order anytime from your OmniMart AI account dashboard. Estimated delivery within 3-5 business days.</p>
                </td></tr>
                <tr><td style="padding:16px 32px;background:#0f172a;text-align:center;font-size:12px;color:#475569;">
                &copy; 2026 OmniMart AI - Autonomous AI-Powered E-Commerce Platform
                </td></tr>
                </table>
                </td></tr></table>
                </body></html>
                """.formatted(orderNumber, safe(name), itemsSummary == null ? "" : itemsSummary, finalAmount);
    }

    private String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}