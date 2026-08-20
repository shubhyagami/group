"""Order lifecycle: checkout, history, tracking details."""
from __future__ import annotations

from fastapi import APIRouter, Cookie, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Order, User
from ..schemas import CheckoutRequest, OrderDto
from ..security import get_current_user
from ..services.cart_service import CartService
from ..services.email_service import brevo_email_service
from ..services.order_service import OrderService

router = APIRouter(tags=["orders"])


@router.post("/checkout", response_model=OrderDto)
def checkout(
    payload: CheckoutRequest,
    cart_session: str | None = Cookie(default=None, alias="cart_session"),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    svc = CartService(db)
    cart, _ = svc.get_or_create(user, cart_session)
    order_svc = OrderService(db)
    try:
        order = order_svc.place_order(user, cart, payload.address_id, payload.payment_method)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    db.commit()

    if payload.email_receipt:
        summary = ", ".join(f"{i.product_name} x{i.quantity}" for i in order.items)[:180]
        brevo_email_service.send_order_confirmation_email(
            user.email, user.full_name, order.order_number, float(order.final_amount), summary
        )

    return OrderDto(**order_svc.order_to_dict(order))


@router.get("/orders", response_model=list[OrderDto])
def my_orders(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    orders = (
        db.query(Order)
        .filter(Order.user_id == user.id)
        .order_by(Order.created_at.desc())
        .all()
    )
    svc = OrderService(db)
    return [OrderDto(**svc.order_to_dict(o)) for o in orders]


@router.get("/orders/{order_id}", response_model=OrderDto)
def order_detail(order_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    order = db.get(Order, order_id)
    if order is None or order.user_id != user.id:
        raise HTTPException(status_code=404, detail="Order not found")
    return OrderDto(**OrderService(db).order_to_dict(order))