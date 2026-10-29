package com.steph.simeme.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.steph.simeme.dto.BirdeyeMultiPriceResponse;
import com.steph.simeme.entity.Token;
import com.steph.simeme.repository.TokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BirdeyeMarketService {

    private final RestClient birdeyeRestClient;
    private final TokenRepository tokenRepository;
    private final PriceCacheService priceCacheService;

    @Value("${birdeye.api.key}")
    private String apiKey;

    // Berjalan otomatis setiap 5000 milidetik (5 detik), dimulai 2 detik setelah aplikasi menyala
    @Scheduled(fixedDelay = 5000, initialDelay = 2000)
    public void syncFeaturedTokenPrices() {
        List<Token> featuredTokens = tokenRepository.findByIsFeaturedTrue();
        if (featuredTokens.isEmpty()) return;

        // Jika API Key masih default/kosong, jalankan generator harga simulasi agar tetap bisa belajar & testing
        if (apiKey == null || apiKey.isBlank()) {
            simulateMarketMovement(featuredTokens);
            return;
        }

        // Gabungkan semua contract address dengan koma: "addr1,addr2,addr3..."
        String listAddress = featuredTokens.stream()
                .map(Token::getContractAddress)
                .collect(Collectors.joining(","));

        try {
            BirdeyeMultiPriceResponse response = birdeyeRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/defi/multi_price")
                            .queryParam("list_address", listAddress)
                            .queryParam("include_liquidity", "true")
                            .build())
                    .retrieve()
                    .body(BirdeyeMultiPriceResponse.class);

            if (response != null && response.success() && response.data() != null) {
                response.data().forEach((address, priceData) -> {
                    if (priceData != null && priceData.value() != null) {
                        priceCacheService.updatePrice(address, priceData.value(), priceData.liquidity());
                    }
                });
                log.info("Berhasil memperbarui {} harga koin dari Birdeye API", response.data().size());
            }
        } catch (Exception e) {
            log.warn("Gagal mengambil data dari Birdeye API ({}). Menggunakan pergerakan harga simulasi.", e.getMessage());
            simulateMarketMovement(featuredTokens);
        }
    }

    // Fungsi fallback jika belum memasukkan API Key Birdeye
    private void simulateMarketMovement(List<Token> tokens) {
        Map<String, BigDecimal> basePrices = Map.of(
                "BONK", new BigDecimal("0.00002250"),
                "WIF", new BigDecimal("2.45000000"),
                "POPCAT", new BigDecimal("1.38000000"),
                "BOME", new BigDecimal("0.00890000"),
                "MEW", new BigDecimal("0.00950000"),
                "PNUT", new BigDecimal("1.12000000"),
                "GOAT", new BigDecimal("0.78000000"),
                "TRUMP", new BigDecimal("12.50000000"),
                "AI16Z", new BigDecimal("0.42000000"),
                "FWOG", new BigDecimal("0.51000000")
        );

        for (Token token : tokens) {
            BigDecimal currentPrice = priceCacheService.getMarketState(token.getContractAddress())
                    .map(PriceCacheService.TokenMarketState::priceUsd)
                    .orElse(basePrices.getOrDefault(token.getSymbol(), new BigDecimal("0.10")));

            // Fluktuasi acak antara -2.5% hingga +2.5% setiap 5 detik seperti meme coin sungguhan
            double changePercent = ThreadLocalRandom.current().nextDouble(-0.025, 0.025);
            BigDecimal multiplier = BigDecimal.valueOf(1.0 + changePercent);
            BigDecimal newPrice = currentPrice.multiply(multiplier).setScale(10, RoundingMode.HALF_UP);

            priceCacheService.updatePrice(token.getContractAddress(), newPrice, new BigDecimal("2500000"));
        }
        log.debug("Harga di In-Memory Cache diperbarui (Mode Simulasi Aktif)");
    }
}