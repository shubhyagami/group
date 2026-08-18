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
@Table(name = "product_specifications", indexes = {
        @Index(name = "idx_spec_product", columnList = "product_id"),
        @Index(name = "idx_spec_key", columnList = "spec_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "spec_group", length = 80)
    private String specGroup;

    @Column(name = "spec_key", length = 80)
    private String specKey;

    @Column(name = "spec_value", length = 300)
    private String specValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}