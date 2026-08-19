"""User profile, addresses, AI preference management and wishlist."""
from __future__ import annotations

import json

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Address, Product, User, UserPreference, Wishlist, WishlistItem
from ..schemas import (
    AddressDto,
    AddressRequest,
    PreferenceDto,
    PreferenceRequest,
    ProductCardDto,
    ProfileDto,
)
from ..security import get_current_user
from ..services.product_cards import product_to_card

router = APIRouter(tags=["profile"])


@router.get("/profile", response_model=ProfileDto)
def get_profile(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    pref = db.query(UserPreference).filter(UserPreference.user_id == user.id).first()
    return ProfileDto(
        user=user,
        addresses=[AddressDto.model_validate(a) for a in user.addresses],
        preference=(
            PreferenceDto(
                preferred_categories=pref.preferred_categories(),
                preferred_brands=pref.preferred_brands(),
                min_budget=float(pref.min_budget) if pref.min_budget else None,
                max_budget=float(pref.max_budget) if pref.max_budget else None,
                recommendations_enabled=pref.recommendations_enabled,
                behavior_tracking_enabled=pref.behavior_tracking_enabled,
            )
            if pref
            else None
        ),
    )


@router.post("/profile/address", response_model=AddressDto)
def add_address(payload: AddressRequest, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if payload.is_default:
        for a in user.addresses:
            a.is_default = False
    address = Address(user_id=user.id, **payload.model_dump())
    db.add(address)
    if payload.is_default or not user.addresses:
        address.is_default = True
    db.commit()
    return AddressDto.model_validate(address)


@router.put("/profile/preferences", response_model=PreferenceDto)
def update_preferences(
    payload: PreferenceRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    pref = db.query(UserPreference).filter(UserPreference.user_id == user.id).first()
    if pref is None:
        pref = UserPreference(user_id=user.id)
        db.add(pref)
    data = payload.model_dump(exclude_none=True)
    if "preferred_categories" in data:
        pref.preferred_categories_json = json.dumps(data.pop("preferred_categories"))
    if "preferred_brands" in data:
        pref.preferred_brands_json = json.dumps(data.pop("preferred_brands"))
    for key, value in data.items():
        setattr(pref, key, value)
    db.commit()
    return PreferenceDto(
        preferred_categories=pref.preferred_categories(),
        preferred_brands=pref.preferred_brands(),
        min_budget=float(pref.min_budget) if pref.min_budget else None,
        max_budget=float(pref.max_budget) if pref.max_budget else None,
        recommendations_enabled=pref.recommendations_enabled,
        behavior_tracking_enabled=pref.behavior_tracking_enabled,
    )


# ------------------------------------------------------------------ wishlist
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