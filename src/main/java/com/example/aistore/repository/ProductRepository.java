package com.example.aistore.repository;

import com.example.aistore.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySku(String sku);

    List<Product> findByActiveTrueAndFeaturedTrue();

    List<Product> findByActiveTrueAndCategoryIdOrderByRatingDesc(Long categoryId);

    List<Product> findByActiveTrueOrderByRatingDesc(Pageable pageable);

    List<Product> findByActiveTrueOrderByReviewCountDesc(Pageable pageable);

    @Query("select coalesce(max(p.reviewCount), 1) from Product p")
    long maxReviewCount();

    @Query("select p from Product p where p.active = true and lower(p.name) like lower(concat('%', :q, '%'))")
    List<Product> searchByName(@Param("q") String q, Pageable pageable);

    @Query("select p from Product p where p.id in :ids and p.active = true")
    List<Product> findByIds(@Param("ids") List<Long> ids);

    @Query("select p from Product p where p.active = true and p.stock > 0 order by p.reviewCount desc")
    List<Product> findPopularInStock(Pageable pageable);

    long countByActiveTrue();
}