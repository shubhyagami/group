package com.example.aistore.repository;

import com.example.aistore.entity.UserInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserInteractionRepository extends JpaRepository<UserInteraction, Long> {

    List<UserInteraction> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserInteraction> findByUserIdOrderByCreatedAtDesc(Long userId);
}