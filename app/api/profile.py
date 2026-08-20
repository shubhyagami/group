"""Wishlist management."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Product, User, Wishlist, WishlistItem
from ..schemas import ProductCardDto
from ..security import get_current_user
from ..services.product_cards import product_to_card

router = APIRouter(tags=["wishlist"])


def _get_wishlist(user: User, db: Session) -> Wishlist:
    wl = db.query(Wishlist).filter(Wishlist.user_id == user.id).first()
    if wl is None:
        wl = Wishlist(user_id=user.id)
        db.add(wl)
        db.flush()
    return wl


@router.get("/wishlist", response_model=list[ProductCardDto])
def get_wishlist(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    wl = _get_wishlist(user, db)
    products = [item.product for item in wl.items if item.product]
    return [ProductCardDto(**product_to_card(p)) for p in products]


@router.post("/wishlist/add", response_model=list[ProductCardDto])
def add_wishlist(product_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = db.get(Product, product_id)
    if product is None:
        raise HTTPException(status_code=404, detail="Product not found")
    wl = _get_wishlist(user, db)
    exists = db.query(WishlistItem).filter(WishlistItem.wishlist_id == wl.id, WishlistItem.product_id == product_id).first()
    if not exists:
        db.add(WishlistItem(wishlist_id=wl.id, product_id=product_id))
        db.commit()
    return [ProductCardDto(**product_to_card(item.product)) for item in wl.items if item.product]


@router.post("/wishlist/remove", response_model=list[ProductCardDto])
def remove_wishlist(product_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    wl = _get_wishlist(user, db)
    item = db.query(WishlistItem).filter(WishlistItem.wishlist_id == wl.id, WishlistItem.product_id == product_id).first()
    if item:
        db.delete(item)
        db.commit()
    return [ProductCardDto(**product_to_card(i.product)) for i in wl.items if i.product]