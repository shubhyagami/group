"""Storefront: product listing, filtering, detail, review submission."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Brand, Category, Product, Review, User
from ..schemas import (
    BrandDto,
    CategoryDto,
    ProductCardDto,
    ProductDetailDto,
    ProductListResponse,
    ReviewDto,
    ReviewRequest,
)
from ..security import get_optional_user
from ..services.feedback_service import CustomerFeedbackService
from ..services.product_cards import product_to_card
from ..services.recommendation_service import HybridRecommendationService
from ..services.search_service import SearchService

router = APIRouter(tags=["storefront"])


@router.get("/categories", response_model=list[CategoryDto])
def list_categories(db: Session = Depends(get_db)):
    return db.query(Category).filter(Category.active.is_(True)).order_by(Category.display_order).all()


@router.get("/brands", response_model=list[BrandDto])
def list_brands(db: Session = Depends(get_db)):
    return db.query(Brand).filter(Brand.active.is_(True)).order_by(Brand.name).all()


@router.get("/products", response_model=ProductListResponse)
def list_products(
    q: str | None = Query(None),
    category: str | None = Query(None, description="category slug"),
    brand: str | None = Query(None, description="brand slug"),
    min_price: float | None = Query(None),
    max_price: float | None = Query(None),
    min_rating: float | None = Query(None),
    featured: bool | None = Query(None),
    sort: str = Query("relevance"),
    page: int = Query(1, ge=1),
    page_size: int = Query(12, ge=1, le=60),
    db: Session = Depends(get_db),
):
    svc = SearchService(db)
    products, total = svc.apply_filters(
        query=q,
        category_slug=category,
        brand_slug=brand,
        min_price=min_price,
        max_price=max_price,
        min_rating=min_rating,
        featured=featured,
        sort=sort,
        page=page,
        page_size=page_size,
    )
    return ProductListResponse(
        products=[ProductCardDto(**product_to_card(p)) for p in products],
        total=total,
        page=page,
        page_size=page_size,
        filters={"q": q, "category": category, "brand": brand, "min_price": min_price, "max_price": max_price, "min_rating": min_rating, "sort": sort},
    )


@router.get("/products/{slug}", response_model=ProductDetailDto)
def product_detail(
    slug: str,
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    product = db.query(Product).filter(Product.slug == slug, Product.active.is_(True)).first()
    if product is None:
        raise HTTPException(status_code=404, detail="Product not found")

    reviews = (
        db.query(Review)
        .filter(Review.product_id == product.id)
        .order_by(Review.created_at.desc())
        .limit(5)
        .all()
    )
    review_dtos = []
    for r in reviews:
        dto = ReviewDto.model_validate(r)
        dto.user_name = r.user.full_name if r.user else None
        review_dtos.append(dto)

    detail = ProductDetailDto(
        **{
            "id": product.id,
            "name": product.name,
            "slug": product.slug,
            "sku": product.sku,
            "short_description": product.short_description,
            "full_description": product.full_description,
            "price": float(product.price),
            "original_price": float(product.original_price) if product.original_price else None,
            "discount_percentage": product.discount_pct,
            "stock": product.stock,
            "in_stock": product.in_stock,
            "featured": product.featured,
            "rating": product.rating,
            "review_count": product.review_count,
            "tags": product.tags,
            "primary_image_url": product.primary_image_url,
            "category": CategoryDto.model_validate(product.category) if product.category else None,
            "brand": BrandDto.model_validate(product.brand) if product.brand else None,
            "images": [{"id": i.id, "image_url": i.image_url, "alt_text": i.alt_text, "is_primary": i.is_primary} for i in product.images],
            "specifications": [
                {"spec_group": s.spec_group, "spec_key": s.spec_key, "spec_value": s.spec_value}
                for s in sorted(product.specifications, key=lambda s: (s.display_order, s.id))
            ],
            "latest_reviews": review_dtos,
        }
    )
    return detail


@router.get("/products/id/{product_id}/related", response_model=list[ProductCardDto])
def related_products(product_id: int, db: Session = Depends(get_db)):
    product = db.get(Product, product_id)
    if product is None:
        raise HTTPException(status_code=404, detail="Product not found")
    reco = HybridRecommendationService(db)
    return [ProductCardDto(**product_to_card(p)) for p in reco.related_products(product, 4)]


@router.post("/products/{product_id}/reviews", response_model=ReviewDto)
def submit_review(
    product_id: int,
    payload: ReviewRequest,
    user: User = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    if user is None:
        raise HTTPException(status_code=401, detail="Login required to review products")
    product = db.get(Product, product_id)
    if product is None:
        raise HTTPException(status_code=404, detail="Product not found")

    existing = (
        db.query(Review)
        .filter(Review.product_id == product_id, Review.user_id == user.id)
        .first()
    )
    if existing:
        raise HTTPException(status_code=409, detail="You already reviewed this product")

    review = Review(
        product_id=product_id,
        user_id=user.id,
        rating=payload.rating,
        title=payload.title,
        comment=payload.comment,
        verified_purchase=payload.verified_purchase,
    )
    db.add(review)
    db.flush()

    # update product aggregates
    from sqlalchemy import func

    stats = (
        db.query(func.avg(Review.rating), func.count(Review.id))
        .filter(Review.product_id == product_id)
        .first()
    )
    product.rating = round(float(stats[0] or 0), 1)
    product.review_count = int(stats[1] or 0)

    # NLP sentiment analysis
    CustomerFeedbackService(db).analyze_review(review)
    db.commit()

    dto = ReviewDto.model_validate(review)
    dto.user_name = user.full_name
    return dto