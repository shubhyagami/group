"""Cart management for both authenticated users and anonymous sessions."""
from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from ..models import Cart, CartItem, Product, User


def _generate_session_id() -> str:
    return uuid.uuid4().hex


class CartService:
    def __init__(self, db: Session):
        self.db = db

    def get_or_create(self, user: User | None = None, session_id: str | None = None) -> tuple[Cart, str]:
        cart = None
        if user:
            cart = self.db.query(Cart).filter(Cart.user_id == user.id).first()
        if cart is None and session_id:
            cart = self.db.query(Cart).filter(Cart.session_id == session_id).first()
        if cart is None:
            cart = Cart(user_id=user.id if user else None, session_id=session_id or _generate_session_id())
            self.db.add(cart)
            self.db.flush()
        # adopt guest cart into user account
        if user and cart.user_id is None:
            cart.user_id = user.id
        return cart, cart.session_id or _generate_session_id()

    def snapshot(self, cart: Cart) -> dict:
        items = []
        subtotal = 0.0
        for item in cart.items:
            price = float(item.unit_price if item.unit_price is not None else (item.product.price if item.product else 0))
            total = price * item.quantity
            subtotal += total
            items.append(
                {
                    "id": item.id,
                    "product_id": item.product_id,
                    "name": item.product.name if item.product else "Unknown",
                    "image_url": item.product.primary_image_url if item.product else None,
                    "unit_price": price,
                    "quantity": item.quantity,
                    "total_price": round(total, 2),
                    "in_stock": bool(item.product and item.product.in_stock),
                }
            )
        return {
            "id": cart.id,
            "items": items,
            "subtotal": round(subtotal, 2),
            "item_count": sum(i["quantity"] for i in items),
            "session_id": cart.session_id,
        }

    def add_item(self, cart: Cart, product_id: int, quantity: int = 1) -> dict:
        product = self.db.get(Product, product_id)
        if product is None or not product.active:
            raise ValueError("Product not found")
        if quantity > product.stock:
            raise ValueError(f"Only {product.stock} units available in stock")
        item = self.db.query(CartItem).filter(CartItem.cart_id == cart.id, CartItem.product_id == product_id).first()
        if item:
            new_qty = item.quantity + quantity
            if new_qty > product.stock:
                raise ValueError(f"Only {product.stock} units available in stock")
            item.quantity = new_qty
        else:
            self.db.add(
                CartItem(
                    cart_id=cart.id,
                    product_id=product_id,
                    quantity=quantity,
                    unit_price=product.price,
                )
            )
        self.db.flush()
        return self.snapshot(cart)

    def update_item(self, cart: Cart, product_id: int, quantity: int) -> dict:
        item = self.db.query(CartItem).filter(CartItem.cart_id == cart.id, CartItem.product_id == product_id).first()
        if item is None:
            raise ValueError("Item not in cart")
        if quantity <= 0:
            self.db.delete(item)
        else:
            product = self.db.get(Product, product_id)
            if product and quantity > product.stock:
                raise ValueError(f"Only {product.stock} units available in stock")
            item.quantity = quantity
        self.db.flush()
        return self.snapshot(cart)

    def remove_item(self, cart: Cart, product_id: int) -> dict:
        item = self.db.query(CartItem).filter(CartItem.cart_id == cart.id, CartItem.product_id == product_id).first()
        if item:
            self.db.delete(item)
            self.db.flush()
        return self.snapshot(cart)

    def clear(self, cart: Cart) -> None:
        for item in list(cart.items):
            self.db.delete(item)
        self.db.flush()