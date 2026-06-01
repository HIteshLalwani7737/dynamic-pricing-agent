package com.hitesh.pricing.controller;

import com.hitesh.pricing.model.PricingDecision;
import com.hitesh.pricing.model.Product;
import com.hitesh.pricing.repository.ProductRepository;
import com.hitesh.pricing.repository.PricingDecisionRepository;
import com.hitesh.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;
    private final ProductRepository productRepository;
    private final PricingDecisionRepository pricingDecisionRepository;

    @PostMapping("/products")
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        return ResponseEntity.ok(productRepository.save(product));
    }

    @PostMapping("/reprice/{sku}")
    public ResponseEntity<PricingDecision> repriceSku(@PathVariable String sku) {
        return productRepository.findBySku(sku)
            .map(product -> ResponseEntity.ok(pricingService.updateProductPrice(product)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history/{sku}")
    public ResponseEntity<List<PricingDecision>> getPricingHistory(@PathVariable String sku) {
        return ResponseEntity.ok(pricingDecisionRepository.findByProductSkuOrderByDecidedAtDesc(sku));
    }

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productRepository.findAll());
    }
}
