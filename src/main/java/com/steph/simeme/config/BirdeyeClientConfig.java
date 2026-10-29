package com.steph.simeme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BirdeyeClientConfig {

    // Mengambil nilai dari application.properties
    @Value("${birdeye.api.base-url}")
    private String baseUrl;

    @Value("${birdeye.api.key}")
    private String apiKey;

    @Bean
    public RestClient birdeyeRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-KEY", apiKey)
                .defaultHeader("x-chain", "solana")
                .defaultHeader("accept", "application/json")
                .build();
    }
}