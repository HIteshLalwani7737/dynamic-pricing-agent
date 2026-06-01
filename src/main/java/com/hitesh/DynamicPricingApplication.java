package com.hitesh.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DynamicPricingApplication {
    public static void main(String[] args) {
        SpringApplication.run(DynamicPricingApplication.class, args);
    }
}
