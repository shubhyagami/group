package com.example.aistore.repository;

import com.example.aistore.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<ChatMessage> findTop10ByConversationIdOrderByCreatedAtAsc(Long conversationId);
}