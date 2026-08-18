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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_feedback", indexes = {
        @Index(name = "idx_feedback_product", columnList = "product_id"),
        @Index(name = "idx_feedback_sentiment", columnList = "sentiment"),
        @Index(name = "idx_feedback_topic", columnList = "primary_topic")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 20)
    private String sentiment;

    @Column(length = 20)
    private String emotion;

    @Column(name = "primary_topic", length = 40)
    private String primaryTopic;

    @Column(name = "specific_issues_json", columnDefinition = "TEXT")
    private String specificIssuesJson;

    @Column(name = "positive_aspects_json", columnDefinition = "TEXT")
    private String positiveAspectsJson;

    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore;

    @Column(length = 20)
    private String source;
}