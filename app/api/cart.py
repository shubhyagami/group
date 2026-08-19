"""Cart APIs with anonymous-session carts that adopt into user accounts."""
from __future__ import annotations

from fastapi import APIRouter, Cookie, Depends, HTTPException, Response
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import User
from ..schemas import CartAddRequest, CartDto, CartUpdateRequest
from ..security import get_optional_user
from ..services.cart_service import CartService, _generate_session_id

router = APIRouter(tags=["cart"])


def _cart_deps(
    response: Response,
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
):
    session_id = cart_session or _generate_session_id()
    response.set_cookie(
        "cart_session",
        session_id,
        max_age=30 * 24 * 3600,
        httponly=True,
        samesite="lax",
    )
    return session_id


@router.get("/cart", response_model=CartDto)
def view_cart(
    response: Response,
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    session_id = _cart_deps(response, cart_session)
    cart, _ = CartService(db).get_or_create(user, session_id)
    return CartDto(**CartService(db).snapshot(cart))


@router.post("/cart/add", response_model=CartDto)
def add_to_cart(
    payload: CartAddRequest,
    response: Response,
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    session_id = _cart_deps(response, cart_session)
    svc = CartService(db)
    cart, _ = svc.get_or_create(user, session_id)
    try:
        snapshot = svc.add_item(cart, payload.product_id, payload.quantity)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    db.commit()
    return CartDto(**snapshot)


@router.post("/cart/update", response_model=CartDto)
def update_cart(
    payload: CartUpdateRequest,
    response: Response,
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    session_id = _cart_deps(response, cart_session)
    svc = CartService(db)
    cart, _ = svc.get_or_create(user, session_id)
    try:
        snapshot = svc.update_item(cart, payload.product_id, payload.quantity)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    db.commit()
    return CartDto(**snapshot)


@router.post("/cart/remove", response_model=CartDto)
def remove_from_cart(
    payload: CartUpdateRequest,
    response: Response,
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    session_id = _cart_deps(response, cart_session)
    svc = CartService(db)
    cart, _ = svc.get_or_create(user, session_id)
    snapshot = svc.remove_item(cart, payload.product_id)
    db.commit()
    return CartDto(**snapshot)