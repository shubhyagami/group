package com.example.aistore.repository;

import com.example.aistore.entity.ProductSpecification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {

    List<ProductSpecification> findByProductIdOrderByDisplayOrderAsc(Long productId);

    List<ProductSpecification> findByProductIdInOrderByDisplayOrderAsc(List<Long> productIds);
}