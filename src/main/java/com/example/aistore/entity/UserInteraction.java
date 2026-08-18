package com.example.aistore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "user_interactions", indexes = {
        @Index(name = "idx_interaction_user", columnList = "user_id"),
        @Index(name = "idx_interaction_product", columnList = "product_id"),
        @Index(name = "idx_interaction_type", columnList = "interaction_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserInteraction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "session_id", length = 120)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 30)
    private InteractionType interactionType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "category_name", length = 80)
    private String categoryName;

    @Column(name = "brand_name", length = 80)
    private String brandName;

    @Column(name = "search_query", length = 300)
    private String searchQuery;

    @Column(name = "duration_seconds")
    private int durationSeconds;
}