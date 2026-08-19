"""Authentication: register, session login/logout, current user."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from ..database import get_db
from ..enums import UserRole
from ..models import User, UserPreference
from ..schemas import LoginRequest, RegisterRequest, UserDto
from ..security import get_current_user, hash_password, login_user, logout_user, verify_password

router = APIRouter(tags=["auth"])


@router.post("/register", response_model=UserDto)
def register(payload: RegisterRequest, db: Session = Depends(get_db)):
    existing = db.query(User).filter(User.email.ilike(payload.email.strip())).first()
    if existing:
        raise HTTPException(status_code=409, detail="Email already registered")
    user = User(
        email=payload.email.strip().lower(),
        password=hash_password(payload.password),
        full_name=payload.full_name.strip(),
        phone=payload.phone,
        active=True,
        roles=[UserRole.ROLE_USER.value],
    )
    db.add(user)
    db.flush()
    db.add(UserPreference(user_id=user.id, recommendations_enabled=True, behavior_tracking_enabled=True))
    db.commit()
    return UserDto.model_validate(user)


@router.post("/login", response_model=UserDto)
def login(payload: LoginRequest, request: Request, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email.ilike(payload.email.strip())).first()
    if user is None or not user.active or not verify_password(payload.password, user.password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password")
    login_user(request, user)
    return UserDto.model_validate(user)


@router.post("/logout")
def logout(request: Request):
    logout_user(request)
    return {"message": "Logged out"}


@router.get("/me", response_model=UserDto)
def me(user: User = Depends(get_current_user)):
    return UserDto.model_validate(user)