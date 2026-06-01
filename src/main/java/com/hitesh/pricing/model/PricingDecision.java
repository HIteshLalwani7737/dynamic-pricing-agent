package com.hitesh.pricing.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "pricing_decisions")
public class PricingDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String productSku;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal lowestCompetitorPrice;
    private String decisionReason;
    private String aiAnalysis;
    private boolean ruleViolationPrevented;
    private LocalDateTime decidedAt;
}
