package com.example.aistore.repository;

import com.example.aistore.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<SearchHistory> findTop10ByOrderByCreatedAtDesc();
}