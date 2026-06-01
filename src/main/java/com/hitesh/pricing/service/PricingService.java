package com.hitesh.pricing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitesh.pricing.agent.CompetitorCrawlerAgent;
import com.hitesh.pricing.agent.PricingAiAgent;
import com.hitesh.pricing.model.CompetitorPrice;
import com.hitesh.pricing.model.PricingDecision;
import com.hitesh.pricing.model.Product;
import com.hitesh.pricing.rule.PricingRuleEngine;
import com.hitesh.pricing.repository.ProductRepository;
import com.hitesh.pricing.repository.CompetitorPriceRepository;
import com.hitesh.pricing.repository.PricingDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final ProductRepository productRepository;
    private final CompetitorPriceRepository competitorPriceRepository;
    private final PricingDecisionRepository pricingDecisionRepository;
    private final CompetitorCrawlerAgent crawlerAgent;
    private final PricingAiAgent aiAgent;
    private final PricingRuleEngine ruleEngine;
    private final ObjectMapper objectMapper;

    /**
     * Scheduled job: runs every 30 minutes to update prices.
     */
    @Scheduled(fixedDelayString = "${pricing.schedule.interval-ms:1800000}")
    public void runPricingCycle() {
        log.info("=== Starting pricing cycle ===");
        List<Product> activeProducts = productRepository.findByActiveTrue();
        activeProducts.forEach(this::updateProductPrice);
        log.info("=== Pricing cycle complete. Updated {} products ===", activeProducts.size());
    }

    /**
     * Update price for a single product. Can be triggered manually or by scheduler.
     */
    public PricingDecision updateProductPrice(Product product) {
        log.info("Repricing SKU: {}", product.getSku());

        // Step 1: Crawl competitor prices
        Map<String, String> competitorUrls = Map.of(
            "amazon", "https://www.amazon.in/s?k=" + product.getSku(),
            "flipkart", "https://www.flipkart.com/search?q=" + product.getSku()
        );

        List<CompetitorPrice> competitorPrices = crawlerAgent.crawlCompetitorPrices(
            product.getSku(), competitorUrls
        );
        competitorPriceRepository.saveAll(competitorPrices);

        // Step 2: AI analysis
        String aiAnalysis = aiAgent.analyzeAndRecommendPrice(product, competitorPrices);
        BigDecimal aiRecommendedPrice = parseAiRecommendedPrice(aiAnalysis, product.getCurrentPrice());

        // Step 3: Apply pricing rules (rule engine validates and enforces constraints)
        boolean ruleViolation = ruleEngine.isRuleViolation(product, aiRecommendedPrice);
        BigDecimal finalPrice = ruleEngine.applyRules(product, aiRecommendedPrice, competitorPrices);

        BigDecimal lowestCompetitor = competitorPrices.stream()
            .map(CompetitorPrice::getPrice)
            .min(BigDecimal::compareTo)
            .orElse(null);

        // Step 4: Record decision
        PricingDecision decision = PricingDecision.builder()
            .productSku(product.getSku())
            .oldPrice(product.getCurrentPrice())
            .newPrice(finalPrice)
            .lowestCompetitorPrice(lowestCompetitor)
            .decisionReason(buildDecisionReason(product, finalPrice, aiRecommendedPrice, ruleViolation))
            .aiAnalysis(aiAnalysis)
            .ruleViolationPrevented(ruleViolation)
            .decidedAt(LocalDateTime.now())
            .build();

        // Step 5: Update product price
        product.setCurrentPrice(finalPrice);
        product.setLastPricedAt(LocalDateTime.now());
        productRepository.save(product);

        return pricingDecisionRepository.save(decision);
    }

    private BigDecimal parseAiRecommendedPrice(String aiAnalysis, BigDecimal fallback) {
        try {
            JsonNode node = objectMapper.readTree(aiAnalysis);
            if (node.has("recommendedPrice")) {
                return node.get("recommendedPrice").decimalValue();
            }
        } catch (Exception e) {
            log.warn("Could not parse AI recommended price: {}", e.getMessage());
        }
        return fallback;
    }

    private String buildDecisionReason(Product product, BigDecimal finalPrice,
                                        BigDecimal aiPrice, boolean ruleViolation) {
        if (ruleViolation) {
            return String.format("AI suggested ₹%.2f but margin/floor rule prevented. Final: ₹%.2f", aiPrice, finalPrice);
        }
        return String.format("AI recommended ₹%.2f accepted. Previous: ₹%.2f", finalPrice, product.getCurrentPrice());
    }
}
