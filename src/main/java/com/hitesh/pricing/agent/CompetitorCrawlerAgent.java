package com.hitesh.pricing.agent;

import com.hitesh.pricing.model.CompetitorPrice;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Web crawler agent that scrapes competitor pricing.
 * Uses Jsoup for HTML parsing. In production, configure CSS selectors
 * per competitor site.
 */
@Slf4j
@Component
public class CompetitorCrawlerAgent {

    // Map of competitor -> CSS selector for price element
    private static final Map<String, String> COMPETITOR_PRICE_SELECTORS = Map.of(
        "amazon", "span.a-price-whole",
        "flipkart", "div._30jeq3",
        "snapdeal", "span.payBlkBig"
    );

    /**
     * Crawl competitor prices for a product SKU.
     * Returns list of competitor prices found.
     */
    public List<CompetitorPrice> crawlCompetitorPrices(String sku, Map<String, String> competitorUrls) {
        List<CompetitorPrice> prices = new ArrayList<>();

        for (Map.Entry<String, String> entry : competitorUrls.entrySet()) {
            String competitor = entry.getKey();
            String url = entry.getValue();

            try {
                BigDecimal price = scrapePrice(competitor, url);
                if (price != null) {
                    prices.add(CompetitorPrice.builder()
                        .productSku(sku)
                        .competitorName(competitor)
                        .competitorUrl(url)
                        .price(price)
                        .currency("INR")
                        .crawledAt(LocalDateTime.now())
                        .verified(true)
                        .build());
                    log.info("Scraped {} price for SKU {}: ₹{}", competitor, sku, price);
                }
            } catch (Exception e) {
                log.warn("Failed to crawl {} for SKU {}: {}", competitor, sku, e.getMessage());
                // Fall back to mock price for demo
                prices.add(generateMockPrice(sku, competitor, url));
            }
        }
        return prices;
    }

    private BigDecimal scrapePrice(String competitor, String url) throws Exception {
        Document doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (compatible; PricingBot/1.0)")
            .timeout(5000)
            .get();

        String selector = COMPETITOR_PRICE_SELECTORS.getOrDefault(competitor, "span.price");
        String priceText = doc.select(selector).first() != null
            ? doc.select(selector).first().text()
            : null;

        if (priceText != null) {
            // Clean price string: remove currency symbols, commas
            String cleaned = priceText.replaceAll("[^0-9.]", "");
            return new BigDecimal(cleaned);
        }
        return null;
    }

    private CompetitorPrice generateMockPrice(String sku, String competitor, String url) {
        // Simulate competitor prices within ±20% of a base
        double basePrice = 1000.0 + (sku.hashCode() % 5000);
        double variance = 0.8 + Math.random() * 0.4; // 80-120%
        BigDecimal mockPrice = BigDecimal.valueOf(Math.round(basePrice * variance));

        return CompetitorPrice.builder()
            .productSku(sku)
            .competitorName(competitor)
            .competitorUrl(url)
            .price(mockPrice)
            .currency("INR")
            .crawledAt(LocalDateTime.now())
            .verified(false)
            .build();
    }
}
