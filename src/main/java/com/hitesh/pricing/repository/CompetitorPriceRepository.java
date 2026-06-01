package com.hitesh.pricing.repository;
import com.hitesh.pricing.model.CompetitorPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CompetitorPriceRepository extends JpaRepository<CompetitorPrice, Long> {
    List<CompetitorPrice> findByProductSkuOrderByCrawledAtDesc(String sku);
    List<CompetitorPrice> findByProductSkuAndCompetitorName(String sku, String competitor);
}
