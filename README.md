# Dynamic Pricing Agent

A **runtime AI agent** built with **Spring Boot** and the **OpenAI Java SDK** that automatically adjusts product prices by crawling competitor websites, analyzing market data with GPT, and enforcing margin/floor pricing rules.

---

## Architecture Overview

```
Scheduler (every 30 min)  ──OR──  Manual API Trigger
              │
              ▼
   CompetitorCrawlerAgent (Jsoup)
   ├── Scrape Amazon price
   └── Scrape Flipkart price
              │
              ▼
   PricingAiAgent (OpenAI GPT-4o-mini)
   └── Prompt: "Given our cost ₹X, margin Y%, competitors at ₹A, ₹B... recommend price"
              │
              ▼
   PricingRuleEngine (Rule Validation)
   ├── Rule 1: price >= cost * (1 + minMargin%)
   ├── Rule 2: price >= absoluteMinimumPrice
   └── Rule 3: price <= lowestCompetitor * 1.20
              │
              ▼
   Final Price Written to Product + Decision Logged
```

---

## Key Features

### ✅ Competitor Web Crawling
- Uses **Jsoup** to scrape competitor product pages
- Configurable CSS selectors per competitor (Amazon, Flipkart, Snapdeal)
- Graceful fallback with mock data if scraping fails (robots.txt compliance)
- Crawl results persisted to DB with timestamp for audit

### ✅ AI-Powered Pricing via OpenAI SDK
- Uses `gpt-4o-mini` for cost-efficient runtime pricing analysis
- Structured JSON response (`response_format: json_object`) for reliable parsing
- Prompt includes: product name, cost price, current price, margin rules, competitor prices
- Fallback to rule-based pricing if OpenAI API is unavailable

### ✅ Rule Engine (Margin Protection)
Three hard rules enforced regardless of AI recommendation:

| Rule | Description |
|------|-------------|
| Minimum Margin | `price >= costPrice * (1 + minMarginPercent / 100)` |
| Absolute Floor | `price >= minAbsolutePrice` |
| Competitor Ceiling | `price <= lowestCompetitorPrice * 1.20` |

### ✅ Full Audit Trail
- Every pricing decision logged to `pricing_decisions` table
- Tracks: old price, new price, AI analysis text, whether a rule violation was prevented, lowest competitor price

### ✅ Scheduled + On-Demand Pricing
- Scheduler runs every 30 minutes (configurable via `pricing.schedule.interval-ms`)
- Manual reprice via `POST /api/v1/pricing/reprice/{sku}`

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2 |
| AI | OpenAI Java SDK (gpt-4o-mini) |
| Web Scraping | Jsoup 1.17 |
| Database | H2 / PostgreSQL |
| Build | Maven, Java 17 |

---

## API Reference

### POST `/api/v1/pricing/products` — Add Product
```json
{
  "sku": "LAPTOP-001",
  "name": "Dell Inspiron 15",
  "category": "Electronics",
  "costPrice": 45000,
  "currentPrice": 59999,
  "minMarginPercent": 15,
  "minAbsolutePrice": 52000,
  "active": true
}
```

### POST `/api/v1/pricing/reprice/{sku}` — Trigger Repricing
Manually trigger the full AI pricing pipeline for a specific SKU.

**Response:**
```json
{
  "id": 1,
  "productSku": "LAPTOP-001",
  "oldPrice": 59999,
  "newPrice": 57499,
  "lowestCompetitorPrice": 58000,
  "decisionReason": "AI recommended ₹57499 accepted. Previous: ₹59999",
  "aiAnalysis": "{\"recommendedPrice\":57499,\"reasoning\":\"Undercut competitor by 1% while maintaining 27% margin\",\"strategy\":\"Competitive pricing\"}",
  "ruleViolationPrevented": false,
  "decidedAt": "2024-01-15T14:30:00"
}
```

### GET `/api/v1/pricing/history/{sku}` — Pricing History
Returns all historical pricing decisions for a SKU, newest first.

---

## Setup & Configuration

### 1. Set OpenAI API Key
```bash
export OPENAI_API_KEY=sk-your-actual-key-here
```

Or in `application.yml`:
```yaml
openai:
  api:
    key: sk-your-key-here
  model: gpt-4o-mini
```

### 2. Run
```bash
mvn spring-boot:run
```

### 3. Add a product and watch it get repriced
```bash
# Add product
curl -X POST http://localhost:8081/api/v1/pricing/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"PHONE-001","name":"Samsung Galaxy","costPrice":20000,"currentPrice":29999,"minMarginPercent":20,"minAbsolutePrice":24000,"active":true}'

# Trigger repricing
curl -X POST http://localhost:8081/api/v1/pricing/reprice/PHONE-001
```

---

## AI Prompt Design

The agent sends this prompt to GPT-4o-mini:
```
Product: Samsung Galaxy (SKU: PHONE-001)
Our cost price: ₹20000.00
Current listed price: ₹29999.00
Minimum margin required: 20.0%
Minimum absolute floor price: ₹24000.00
Competitor prices: amazon: ₹27500.00, flipkart: ₹26999.00

Recommend optimal price to stay competitive while maintaining profitability.
```

GPT responds with:
```json
{
  "recommendedPrice": 26799,
  "reasoning": "Undercutting Flipkart by ₹200 while maintaining 34% margin above cost",
  "strategy": "Penetration pricing — compete on lowest competitor"
}
```

---

## Interview Talking Points

1. **Why OpenAI over a simple formula?** AI can factor in nuanced signals — category trends, product positioning, seasonal demand patterns — that a hardcoded formula can't.

2. **Why a rule engine on top of AI?** AI recommendations can't be fully trusted with financial decisions. Rule engine provides guardrails to prevent pricing below cost.

3. **Jsoup vs headless browser?** Jsoup is lightweight for static HTML. For JS-rendered pages (React storefronts), Playwright/Selenium would be needed — designed as a pluggable interface.

4. **Rate limiting crawls?** In production, add Resilience4j rate limiter per competitor + respect robots.txt + add proxy rotation.
