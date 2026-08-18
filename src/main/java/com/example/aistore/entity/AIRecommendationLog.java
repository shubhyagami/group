package com.example.aistore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_recommendation_logs", indexes = {
        @Index(name = "idx_ailog_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AIRecommendationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "query_text", columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "tool_used", length = 40)
    private String toolUsed;

    @Column(name = "product_ids_json", columnDefinition = "TEXT")
    private String productIdsJson;

    @Column(name = "generated_reasoning", columnDefinition = "TEXT")
    private String generatedReasoning;

    @Column(name = "provider_used", length = 40)
    private String providerUsed;

    @Column(name = "execution_time_ms")
    private long executionTimeMs;
}