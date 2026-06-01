package com.hitesh.pricing.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String sku;
    private String name;
    private String category;
    private BigDecimal costPrice;
    private BigDecimal currentPrice;
    private BigDecimal minMarginPercent;
    private BigDecimal minAbsolutePrice;
    private LocalDateTime lastPricedAt;
    private boolean active;
}
