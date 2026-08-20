"""Brevo (Sendinblue) transactional email service with dark HTML templates."""
from __future__ import annotations

import logging

import httpx

from ..config import settings

logger = logging.getLogger(__name__)

BREVO_URL = "https://api.brevo.com/v3/smtp/email"


def _template_shell(title: str, body_html: str) -> str:
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title></head>
<body style="margin:0;padding:0;background-color:#0b1020;font-family:Segoe UI,Arial,Helvetica,sans-serif;">
<div style="max-width:600px;margin:0 auto;background-color:#111827;border:1px solid #1f2937;border-radius:14px;overflow:hidden;">
  <div style="background:linear-gradient(135deg,#7c3aed,#db2777);padding:22px 28px;">
    <div style="color:#ffffff;font-size:22px;font-weight:700;letter-spacing:0.5px;">&#9889; OmniMart AI</div>
    <div style="color:#e9d5ff;font-size:13px;margin-top:2px;">Autonomous AI-Powered E-Commerce Platform</div>
  </div>
  <div style="padding:26px 28px;color:#e5e7eb;font-size:15px;line-height:1.6;">
    {body_html}
  </div>
  <div style="padding:16px 28px;border-top:1px solid #1f2937;color:#6b7280;font-size:12px;">
    &copy; 2026 OmniMart AI &middot; You received this email because of activity on your account.<br>
    Need help? Contact support@omnimart.com
  </div>
</div></body></html>"""


def _invoice_template(recipient_name: str, order_number: str, final_amount: float, items_summary: str, tracking: str = "") -> str:
    body = f"""
    <p>Hello <strong>{recipient_name or 'there'}</strong>,</p>
    <p>Your order has been placed successfully! Here's your receipt:</p>
    <div style="background:#0b1020;border-radius:10px;padding:18px;margin:14px 0;">
      <table style="width:100%;border-collapse:collapse;color:#e5e7eb;font-size:14px;">
        <tr><td style="padding:6px 0;color:#9ca3af;">Order Number</td><td style="padding:6px 0;text-align:right;font-weight:600;">{order_number}</td></tr>
        <tr><td style="padding:6px 0;color:#9ca3af;">Items</td><td style="padding:6px 0;text-align:right;">{items_summary}</td></tr>
        <tr><td style="padding:6px 0;color:#9ca3af;">Amount Paid</td><td style="padding:6px 0;text-align:right;font-size:17px;font-weight:800;color:#a78bfa;">&#8377;{final_amount:,.2f}</td></tr>
      </table>
    </div>
    {f'<p style="color:#9ca3af;font-size:13px;">Tracking: <strong style="color:#e5e7eb;">{tracking}</strong></p>' if tracking else ''}
    <p style="color:#9ca3af;font-size:13px;">Track your order anytime from your OmniMart AI profile. Estimated delivery in 4-6 business days.</p>
    """
    return _template_shell(f"Invoice {order_number} | OmniMart AI", body)


class BrevoEmailService:
    """Transactional email via Brevo SMTP API v3. Safe no-op fallback when
    no API key is configured (keeps local demos working)."""

    def __init__(self, api_key: str | None = None):
        self.api_key = api_key if api_key is not None else settings.raw_brevo_api_key
        self.sender_email = settings.BREVO_SENDER_EMAIL
        self.sender_name = settings.BREVO_SENDER_NAME

    @property
    def enabled(self) -> bool:
        return bool(self.api_key)

    def _payload(self, to_email: str, to_name: str, subject: str, html: str) -> dict:
        return {
            "sender": {"email": self.sender_email, "name": self.sender_name},
            "to": [{"email": to_email, "name": to_name or to_email}],
            "subject": subject,
            "htmlContent": html,
        }

    def _dispatch(self, payload: dict) -> bool:
        if not self.enabled:
            logger.info("Brevo API key not configured - skipping email to %s", payload["to"][0]["email"])
            return False
        try:
            resp = httpx.post(
                BREVO_URL,
                headers={
                    "api-key": self.api_key,
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                },
                json=payload,
                timeout=8.0,
            )
            if resp.status_code >= 300:
                logger.warning("Brevo rejected email (HTTP %s): %s", resp.status_code, resp.text[:300])
                return False
            return True
        except httpx.HTTPError as exc:
            logger.warning("Brevo unavailable: %s", exc)
            return False

    def send_order_confirmation_email(
        self,
        recipient_email: str,
        recipient_name: str,
        order_number: str,
        final_amount: float,
        items_summary: str,
        tracking: str = "",
    ) -> bool:
        return self._dispatch(
            self._payload(
                recipient_email,
                recipient_name,
                f"Order Confirmed - {order_number} | OmniMart AI",
                _invoice_template(recipient_name, order_number, final_amount, items_summary, tracking),
            )
        )


brevo_email_service = BrevoEmailService()