package com.hitesh.pricing.repository;
import com.hitesh.pricing.model.PricingDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PricingDecisionRepository extends JpaRepository<PricingDecision, Long> {
    List<PricingDecision> findByProductSkuOrderByDecidedAtDesc(String sku);
}
