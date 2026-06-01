package com.hitesh.pricing.agent;

import com.hitesh.pricing.model.CompetitorPrice;
import com.hitesh.pricing.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI agent using OpenAI API to analyze competitor prices and recommend optimal pricing.
 * Uses the openai-java SDK for API communication.
 */
@Slf4j
@Component
public class PricingAiAgent {

    @Value("${openai.api.key:sk-dummy-key-replace-in-production}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Ask the AI agent to analyze competitor prices and recommend the optimal price.
     * The agent considers: competitor prices, our cost, margin rules, market positioning.
     */
    public String analyzeAndRecommendPrice(Product product, List<CompetitorPrice> competitorPrices) {
        String prompt = buildPricingPrompt(product, competitorPrices);
        log.info("Sending pricing analysis request to OpenAI for SKU: {}", product.getSku());

        try {
            String requestBody = String.format("""
                {
                    "model": "%s",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are an expert e-commerce pricing strategist. Analyze competitor prices and recommend optimal pricing. Always respond with JSON containing: recommendedPrice (number), reasoning (string), strategy (string)."
                        },
                        {
                            "role": "user",
                            "content": %s
                        }
                    ],
                    "max_tokens": 500,
                    "response_format": {"type": "json_object"}
                }
                """, model, escapeJson(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("AI pricing analysis completed for SKU: {}", product.getSku());
                return extractContent(response.body());
            } else {
                log.warn("OpenAI API returned status {}: {}", response.statusCode(), response.body());
                return getFallbackAnalysis(product, competitorPrices);
            }
        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            return getFallbackAnalysis(product, competitorPrices);
        }
    }

    private String buildPricingPrompt(Product product, List<CompetitorPrice> competitorPrices) {
        String competitorSummary = competitorPrices.stream()
            .map(cp -> String.format("%s: ₹%.2f", cp.getCompetitorName(), cp.getPrice()))
            .collect(Collectors.joining(", "));

        return String.format(
            "Product: %s (SKU: %s)\\nOur cost price: ₹%.2f\\nCurrent listed price: ₹%.2f\\n" +
            "Minimum margin required: %.1f%%\\nMinimum absolute floor price: ₹%.2f\\n" +
            "Competitor prices: %s\\n\\n" +
            "Recommend optimal price to stay competitive while maintaining profitability.",
            product.getName(), product.getSku(),
            product.getCostPrice(), product.getCurrentPrice(),
            product.getMinMarginPercent(), product.getMinAbsolutePrice(),
            competitorSummary
        );
    }

    private String getFallbackAnalysis(Product product, List<CompetitorPrice> competitorPrices) {
        BigDecimal minCompetitorPrice = competitorPrices.stream()
            .map(CompetitorPrice::getPrice)
            .min(BigDecimal::compareTo)
            .orElse(product.getCurrentPrice());

        return String.format(
            "{\"recommendedPrice\": %.2f, \"reasoning\": \"Rule-based fallback: competitive with lowest competitor\", \"strategy\": \"Competitive pricing\"}",
            minCompetitorPrice.multiply(BigDecimal.valueOf(0.99))
        );
    }

    private String escapeJson(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String extractContent(String responseBody) {
        // Extract the content field from OpenAI response
        int start = responseBody.indexOf("\"content\":") + 11;
        if (start < 11) return responseBody;
        char quote = responseBody.charAt(start);
        int end = responseBody.indexOf(String.valueOf(quote), start + 1);
        return responseBody.substring(start + 1, end);
    }
}
