"""OTP verification: send (Brevo email) + verify (auto-login)."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.orm import Session

from ..database import get_db
from ..schemas import OtpSendRequest, OtpVerifyRequest
from ..security import login_user
from ..services.email_service import brevo_email_service
from ..services.otp_service import otp_store

router = APIRouter(tags=["otp"])


@router.post("/api/otp/send")
def send_otp(payload: OtpSendRequest):
    email = payload.email.strip().lower()
    otp = otp_store.generate(email, payload.purpose)
    sent = brevo_email_service.send_otp_email(email, payload.name, otp, payload.purpose)
    return {
        "message": "OTP sent" if sent else "OTP generated (email dispatch disabled - check server logs)",
        "email": email,
        "email_delivered": sent,
        "expires_in_seconds": otp_store.ttl,
    }


@router.post("/api/otp/verify")
def verify_otp(payload: OtpVerifyRequest, request: Request, db: Session = Depends(get_db)):
    ok, message = otp_store.verify(payload.email.strip().lower(), payload.otp)
    if not ok:
        raise HTTPException(status_code=400, detail=message)

    from ..models import User

    user = db.query(User).filter(User.email.ilike(payload.email.strip())).first()
    if user and user.active:
        login_user(request, user)
        return {"message": "OTP verified", "authenticated": True, "user_id": user.id}
    return {"message": "OTP verified - complete registration to continue", "authenticated": False, "user_id": None}