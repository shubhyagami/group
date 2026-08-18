package com.example.aistore.repository;

import com.example.aistore.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    Optional<Category> findBySlug(String slug);

    List<Category> findByActiveTrueOrderByDisplayOrderAsc();

    List<Category> findByNameContainingIgnoreCase(String name);
}