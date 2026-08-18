package com.example.aistore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "competitor_name", nullable = false, length = 60)
    private String competitorName;

    @Column(name = "competitor_price", precision = 12, scale = 2)
    private BigDecimal competitorPrice;

    @Column(name = "competitor_url", length = 500)
    private String competitorUrl;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock = true;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
}