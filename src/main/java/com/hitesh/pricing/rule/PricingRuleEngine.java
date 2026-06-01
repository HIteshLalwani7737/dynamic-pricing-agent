package com.hitesh.pricing.rule;

import com.hitesh.pricing.model.CompetitorPrice;
import com.hitesh.pricing.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Rule engine that validates pricing decisions.
 * Rules enforced:
 * 1. Price must not be below cost + minimum margin
 * 2. Price must not be below configured absolute minimum
 * 3. Price should be competitive with lowest competitor (within tolerance)
 */
@Slf4j
@Component
public class PricingRuleEngine {

    /**
     * Validate and enforce pricing rules on the AI-recommended price.
     * @return final validated price
     */
    public BigDecimal applyRules(Product product, BigDecimal aiRecommendedPrice,
                                  List<CompetitorPrice> competitorPrices) {
        BigDecimal price = aiRecommendedPrice;

        // Rule 1: Minimum margin rule
        BigDecimal minPriceByMargin = calculateMinPriceByMargin(product);
        if (price.compareTo(minPriceByMargin) < 0) {
            log.warn("SKU {}: AI recommended price ₹{} violates margin rule. Flooring to ₹{}",
                product.getSku(), price, minPriceByMargin);
            price = minPriceByMargin;
        }

        // Rule 2: Absolute minimum price rule
        if (product.getMinAbsolutePrice() != null &&
            price.compareTo(product.getMinAbsolutePrice()) < 0) {
            log.warn("SKU {}: Price ₹{} below absolute minimum ₹{}. Applying floor.",
                product.getSku(), price, product.getMinAbsolutePrice());
            price = product.getMinAbsolutePrice();
        }

        // Rule 3: Don't price more than 20% above lowest competitor
        BigDecimal lowestCompetitor = getLowestCompetitorPrice(competitorPrices);
        if (lowestCompetitor != null) {
            BigDecimal maxAllowed = lowestCompetitor.multiply(BigDecimal.valueOf(1.20));
            if (price.compareTo(maxAllowed) > 0) {
                log.info("SKU {}: Price ₹{} is too high vs competitor ₹{}. Adjusting to competitive range.",
                    product.getSku(), price, lowestCompetitor);
                price = maxAllowed;
                // Re-apply margin check after adjustment
                if (price.compareTo(minPriceByMargin) < 0) {
                    price = minPriceByMargin;
                    log.info("SKU {}: Cannot beat competitor price and maintain margin. Holding at ₹{}",
                        product.getSku(), price);
                }
            }
        }

        return price.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isRuleViolation(Product product, BigDecimal recommendedPrice) {
        BigDecimal minPrice = calculateMinPriceByMargin(product);
        boolean marginViolation = recommendedPrice.compareTo(minPrice) < 0;
        boolean absoluteViolation = product.getMinAbsolutePrice() != null &&
            recommendedPrice.compareTo(product.getMinAbsolutePrice()) < 0;
        return marginViolation || absoluteViolation;
    }

    private BigDecimal calculateMinPriceByMargin(Product product) {
        if (product.getMinMarginPercent() == null) return product.getCostPrice();
        BigDecimal marginMultiplier = BigDecimal.ONE.add(
            product.getMinMarginPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );
        return product.getCostPrice().multiply(marginMultiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getLowestCompetitorPrice(List<CompetitorPrice> prices) {
        return prices.stream()
            .map(CompetitorPrice::getPrice)
            .min(BigDecimal::compareTo)
            .orElse(null);
    }
}
