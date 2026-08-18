package com.example.aistore.repository;

import com.example.aistore.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Modifying
    @Query("delete from CartItem ci where ci.cart.id = :cartId and ci.product.id = :productId")
    int deleteByCartAndProduct(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Modifying
    @Query("delete from CartItem ci where ci.cart.id = :cartId")
    int deleteAllByCartId(@Param("cartId") Long cartId);
}