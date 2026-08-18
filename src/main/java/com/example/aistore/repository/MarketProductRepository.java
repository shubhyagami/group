package com.example.aistore.repository;

import com.example.aistore.entity.MarketProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketProductRepository extends JpaRepository<MarketProduct, Long> {

    List<MarketProduct> findByProductId(Long productId);
}