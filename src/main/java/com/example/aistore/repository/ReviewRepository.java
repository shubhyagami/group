package com.example.aistore.repository;

import com.example.aistore.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    long countByProductId(Long productId);

    @Query("select coalesce(avg(r.rating), 0) from Review r where r.product.id = :productId")
    double averageRatingForProduct(@Param("productId") Long productId);

    @Query("select count(r) from Review r where r.product.id in :productIds")
    long countByProductIds(@Param("productIds") List<Long> productIds);

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}