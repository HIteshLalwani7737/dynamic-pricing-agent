package com.hitesh.pricing.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "competitor_prices")
public class CompetitorPrice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String productSku;
    private String competitorName;
    private String competitorUrl;
    private BigDecimal price;
    private String currency;
    private LocalDateTime crawledAt;
    private boolean verified;
}
