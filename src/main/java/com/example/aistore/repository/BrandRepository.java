package com.example.aistore.repository;

import com.example.aistore.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByName(String name);

    Optional<Brand> findBySlug(String slug);

    List<Brand> findByActiveTrueOrderByNameAsc();

    List<Brand> findByNameContainingIgnoreCase(String name);
}