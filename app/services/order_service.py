"""Order lifecycle: checkout, stock reservation, payment record."""
from __future__ import annotations

import uuid
from datetime import datetime, timedelta

from sqlalchemy.orm import Session

from ..models import Address, Cart, Inventory, Order, OrderItem, Payment, Product, User

SHIPPING_FEE = 49.0
FREE_SHIPPING_ABOVE = 999.0
TAX_RATE = 0.18


def _generate_order_number() -> str:
    return "OM" + datetime.utcnow().strftime("%Y%m%d") + uuid.uuid4().hex[:8].upper()


class OrderService:
    def __init__(self, db: Session):
        self.db = db

    def _stock_for(self, product: Product) -> Inventory:
        inv = self.db.query(Inventory).filter(Inventory.product_id == product.id).first()
        if inv is None:
            inv = Inventory(product=product, stock_quantity=product.stock, reserved_quantity=0)
            self.db.add(inv)
            self.db.flush()
        return inv

    def place_order(
        self,
        user: User,
        cart: Cart,
        address_id: int,
        payment_method: str = "UPI",
    ) -> Order:
        if not cart.items:
            raise ValueError("Cart is empty")

        address = self.db.get(Address, address_id)
        if address is None or address.user_id != user.id:
            raise ValueError("Invalid shipping address")

        # Validate & reserve stock
        total = 0.0
        for item in cart.items:
            product = self.db.get(Product, item.product_id)
            if product is None or not product.active:
                raise ValueError(f"Product {item.product_id} no longer available")
            if product.stock < item.quantity:
                raise ValueError(f"Insufficient stock for {product.name} (only {product.stock} left)")
            inv = self._stock_for(product)
            inv.stock_quantity = product.stock - item.quantity
            inv.reserved_quantity = inv.reserved_quantity + item.quantity
            product.stock -= item.quantity
            if product.stock <= 0:
                product.in_stock = False
            total += float(item.unit_price or product.price) * item.quantity

        discount = 0.0
        for item in cart.items:
            if item.product and item.product.original_price:
                discount += (float(item.product.original_price) - float(item.product.price)) * item.quantity
        discount = max(0.0, discount)

        shipping = 0.0 if total >= FREE_SHIPPING_ABOVE else SHIPPING_FEE
        tax = total * TAX_RATE
        final = total - discount + tax + shipping

        order = Order(
            order_number=_generate_order_number(),
            user_id=user.id,
            total_amount=round(total, 2),
            discount_amount=round(discount, 2),
            tax_amount=round(tax, 2),
            shipping_fee=round(shipping, 2),
            final_amount=round(final, 2),
            status="PENDING",
            shipping_address_id=address.id,
            estimated_delivery_date=datetime.utcnow() + timedelta(days=5),
        )
        self.db.add(order)
        self.db.flush()

        for item in cart.items:
            product = self.db.get(Product, item.product_id)
            order.items.append(
                OrderItem(
                    order_id=order.id,
                    product_id=item.product_id,
                    product_name=product.name if product else item.product_id,
                    product_image_url=product.primary_image_url if product else None,
                    quantity=item.quantity,
                    unit_price=item.unit_price or product.price,
                    total_price=round(float(item.unit_price or product.price) * item.quantity, 2),
                )
            )

        payment = Payment(
            order_id=order.id,
            payment_method=payment_method,
            transaction_id="TXN-" + uuid.uuid4().hex[:12].upper(),
            amount=round(final, 2),
            status="COMPLETED" if payment_method != "COD" else "PENDING",
            paid_at=datetime.utcnow() if payment_method != "COD" else None,
        )
        order.payment = payment

        # Clear cart
        for item in list(cart.items):
            self.db.delete(item)
        self.db.flush()
        return order

    def order_to_dict(self, order: Order) -> dict:
        return {
            "id": order.id,
            "order_number": order.order_number,
            "total_amount": float(order.total_amount),
            "discount_amount": float(order.discount_amount),
            "tax_amount": float(order.tax_amount),
            "shipping_fee": float(order.shipping_fee),
            "final_amount": float(order.final_amount),
            "status": order.status,
            "carrier": order.carrier,
            "tracking_number": order.tracking_number,
            "cancellation_reason": order.cancellation_reason,
            "return_reason": order.return_reason,
            "return_status": order.return_status,
            "estimated_delivery_date": order.estimated_delivery_date,
            "delivered_at": order.delivered_at,
            "created_at": order.created_at,
            "items": [
                {
                    "id": i.id,
                    "product_id": i.product_id,
                    "product_name": i.product_name,
                    "product_image_url": i.product_image_url,
                    "quantity": i.quantity,
                    "unit_price": float(i.unit_price),
                    "total_price": float(i.total_price),
                }
                for i in order.items
            ],
            "payment": (
                {
                    "id": order.payment.id,
                    "payment_method": order.payment.payment_method,
                    "transaction_id": order.payment.transaction_id,
                    "amount": float(order.payment.amount),
                    "status": order.payment.status,
                    "paid_at": order.payment.paid_at,
                }
                if order.payment
                else None
            ),
            "shipping_address": (
                {
                    "id": order.shipping_address.id,
                    "full_name": order.shipping_address.full_name,
                    "street_address": order.shipping_address.street_address,
                    "apartment": order.shipping_address.apartment,
                    "city": order.shipping_address.city,
                    "state": order.shipping_address.state,
                    "postal_code": order.shipping_address.postal_code,
                    "country": order.shipping_address.country,
                    "phone": order.shipping_address.phone,
                    "address_type": order.shipping_address.address_type,
                    "is_default": order.shipping_address.is_default,
                }
                if order.shipping_address
                else None
            ),
        }