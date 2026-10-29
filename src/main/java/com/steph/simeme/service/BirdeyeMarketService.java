package com.steph.simeme.service;

import com.steph.simeme.dto.AddTokenRequest;
import com.steph.simeme.dto.BirdeyePriceResponse;
import com.steph.simeme.dto.BirdeyeTokenMetadataResponse;
import com.steph.simeme.dto.DexScreenerTokenResponse;
import com.steph.simeme.entity.Token;
import com.steph.simeme.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class BirdeyeMarketService {

    private final RestClient birdeyeRestClient;
    private final TokenRepository tokenRepository;
    private final PriceCacheService priceCacheService;
    private final MarketBroadcastService marketBroadcastService;

    private final RestClient dexScreenerRestClient = RestClient.builder()
            .baseUrl("https://api.dexscreener.com")
            .defaultHeader("accept", "application/json")
            .build();

    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    @Value("${birdeye.api.key}")
    private String apiKey;

    private record ResolvedMetadata(
            String symbol,
            String name,
            Integer decimals,
            String logoUri,
            BigDecimal fallbackPriceUsd,
            BigDecimal fallbackLiquidityUsd
    ) {}

    /**
     * Mengambil harga real-time dari Birdeye menggunakan endpoint GET /defi/price.
     * Dijalankan secara bergilir (round-robin) setiap 1.2 detik agar sesuai dengan
     * batas rate-limit Birdeye Standard Tier (1 RPS) tanpa simulasi hardcoded.
     */
    @Scheduled(fixedDelay = 1200, initialDelay = 1000)
    public void syncFeaturedTokenPrices() {
        List<Token> featuredTokens = tokenRepository.findByIsFeaturedTrue();
        if (featuredTokens.isEmpty()) return;

        if (apiKey == null || apiKey.isBlank() || apiKey.equals("ISI_API_KEY_BIRDEYE_KAMU_NANTI")) {
            log.warn("Birdeye API Key belum dikonfigurasi di application.properties!");
            return;
        }

        // 1. Jika ada token yang belum memiliki harga di cache (misal saat aplikasi baru menyala),
        //    ambil harganya satu per satu dengan jeda 1.1 detik agar tidak terkena HTTP 429 (Rate Limit).
        List<Token> uncachedTokens = featuredTokens.stream()
                .filter(t -> priceCacheService.getMarketState(t.getContractAddress()).isEmpty())
                .toList();

        if (!uncachedTokens.isEmpty()) {
            for (int i = 0; i < uncachedTokens.size(); i++) {
                Token token = uncachedTokens.get(i);
                fetchAndCacheSingleTokenPrice(token);
                marketBroadcastService.broadcastMarketPrices(featuredTokens);

                if (i < uncachedTokens.size() - 1) {
                    try {
                        Thread.sleep(1100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            return;
        }

        // 2. Setelah semua token memiliki harga awal, perbarui secara bergilir (round-robin)
        //    menggunakan GET /defi/price dan pancarkan pembaruan via WebSocket.
        int index = Math.floorMod(roundRobinIndex.getAndIncrement(), featuredTokens.size());
        Token targetToken = featuredTokens.get(index);

        fetchAndCacheSingleTokenPrice(targetToken);
        marketBroadcastService.broadcastMarketPrices(featuredTokens);
    }

    /**
     * Memanggil GET /defi/price?address={ca}&include_liquidity=true untuk 1 token
     */
    public Optional<BirdeyePriceResponse.PriceData> fetchAndCacheSingleTokenPrice(Token token) {
        Optional<BirdeyePriceResponse.PriceData> priceDataOpt = fetchPriceByAddress(token.getContractAddress());
        priceDataOpt.ifPresent(priceData -> {
            token.setLastKnownPrice(priceData.value());
            token.setUpdatedAt(LocalDateTime.now());
            tokenRepository.save(token);
        });
        return priceDataOpt;
    }

    /**
     * Memanggil GET /defi/price?address={contractAddress}&include_liquidity=true ke Birdeye
     * dan langsung memperbarui PriceCacheService.
     */
    public Optional<BirdeyePriceResponse.PriceData> fetchPriceByAddress(String contractAddress) {
        try {
            BirdeyePriceResponse response = birdeyeRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/defi/price")
                            .queryParam("address", contractAddress)
                            .queryParam("include_liquidity", "true")
                            .build())
                    .retrieve()
                    .body(BirdeyePriceResponse.class);

            if (response != null && response.success() && response.data() != null && response.data().value() != null) {
                BirdeyePriceResponse.PriceData priceData = response.data();
                priceCacheService.updatePrice(contractAddress, priceData.value(), priceData.liquidity());
                return Optional.of(priceData);
            } else {
                log.warn("Respons Birdeye GET /defi/price kosong untuk token: {}", contractAddress);
            }
        } catch (Exception e) {
            log.warn("Gagal mengambil harga dari Birdeye GET /defi/price untuk {} ({})", contractAddress, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Fitur Tambah Token berdasarkan Contract Address (CA) yang di-paste user.
     * Otomatis memvalidasi harga ke Birdeye GET /defi/price dan melengkapi metadata koin.
     */
    public Token addTokenByContractAddress(AddTokenRequest request) {
        if (request == null || request.contractAddress() == null || request.contractAddress().isBlank()) {
            throw new IllegalArgumentException("Contract Address (CA) tidak boleh kosong");
        }

        String ca = request.contractAddress().trim();

        // Jika token sudah ada di database, pastikan isFeatured = true dan segarkan harganya dari Birdeye
        Optional<Token> existingTokenOpt = tokenRepository.findById(ca);
        if (existingTokenOpt.isPresent()) {
            Token existingToken = existingTokenOpt.get();
            if (!existingToken.isFeatured()) {
                existingToken.setFeatured(true);
            }
            fetchAndCacheSingleTokenPrice(existingToken);
            marketBroadcastService.broadcastMarketPrices(tokenRepository.findByIsFeaturedTrue());
            return existingToken;
        }

        // 1. Ambil harga pasar real-time dari Birdeye GET /defi/price
        Optional<BirdeyePriceResponse.PriceData> priceOpt = fetchPriceByAddress(ca);

        // 2. Ambil metadata token (symbol, name, decimals, logoUri) dari Birdeye / DexScreener
        ResolvedMetadata metadata = resolveTokenMetadata(ca);

        BigDecimal livePrice = priceOpt.map(BirdeyePriceResponse.PriceData::value)
                .orElse(metadata.fallbackPriceUsd());

        if (livePrice == null || livePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Token dengan Contract Address '" + ca + "' tidak ditemukan atau belum memiliki harga pasar di Birdeye."
            );
        }

        BigDecimal liquidity = priceOpt.map(BirdeyePriceResponse.PriceData::liquidity)
                .orElse(metadata.fallbackLiquidityUsd());

        // Pastikan harga & likuiditas tersimpan di RAM cache
        priceCacheService.updatePrice(ca, livePrice, liquidity);

        String shortCa = ca.length() >= 6 ? ca.substring(0, 6).toUpperCase() : ca.toUpperCase();
        String finalSymbol = firstNonBlank(request.symbol(), metadata.symbol(), shortCa);
        String finalName = firstNonBlank(request.name(), metadata.name(), "Token " + shortCa);
        int finalDecimals = request.decimals() != null ? request.decimals()
                : (metadata.decimals() != null ? metadata.decimals() : 6);
        String finalLogoUri = firstNonBlank(request.logoUri(), metadata.logoUri(), null);

        Token newToken = Token.builder()
                .contractAddress(ca)
                .symbol(finalSymbol)
                .name(finalName)
                .decimals(finalDecimals)
                .logoUri(finalLogoUri)
                .chain("solana")
                .isFeatured(true)
                .lastKnownPrice(livePrice)
                .updatedAt(LocalDateTime.now())
                .build();

        Token savedToken = tokenRepository.save(newToken);
        log.info("TOKEN BARU DITAMBAHKAN: {} ({}) - CA: {} dengan harga live ${}",
                savedToken.getName(), savedToken.getSymbol(), savedToken.getContractAddress(), livePrice);

        // Langsung pancarkan daftar token & harga terbaru ke seluruh client WebSocket
        marketBroadcastService.broadcastMarketPrices(tokenRepository.findByIsFeaturedTrue());

        return savedToken;
    }

    private ResolvedMetadata resolveTokenMetadata(String contractAddress) {
        // Percobaan 1: Birdeye V3 Token Metadata Single
        try {
            BirdeyeTokenMetadataResponse metaResp = birdeyeRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/defi/v3/token/meta-data/single")
                            .queryParam("address", contractAddress)
                            .build())
                    .retrieve()
                    .body(BirdeyeTokenMetadataResponse.class);

            if (metaResp != null && metaResp.success() && metaResp.data() != null
                    && metaResp.data().symbol() != null && !metaResp.data().symbol().isBlank()) {
                var d = metaResp.data();
                return new ResolvedMetadata(
                        d.symbol(),
                        d.name(),
                        d.decimals(),
                        d.resolvedLogoUri(),
                        d.price(),
                        d.liquidity()
                );
            }
        } catch (Exception e) {
            log.debug("Birdeye meta-data/single tidak tersedia untuk {}: {}", contractAddress, e.getMessage());
        }

        // Percobaan 2: DexScreener Public API (Gratis & tanpa limit API key untuk metadata Solana)
        try {
            DexScreenerTokenResponse dexResp = dexScreenerRestClient.get()
                    .uri("/latest/dex/tokens/{ca}", contractAddress)
                    .retrieve()
                    .body(DexScreenerTokenResponse.class);

            if (dexResp != null && dexResp.pairs() != null && !dexResp.pairs().isEmpty()) {
                DexScreenerTokenResponse.PairData pair = dexResp.pairs().get(0);
                String symbol = pair.baseToken() != null ? pair.baseToken().symbol() : null;
                String name = pair.baseToken() != null ? pair.baseToken().name() : null;
                String logoUri = pair.info() != null ? pair.info().imageUrl() : null;
                BigDecimal fallbackPrice = null;
                if (pair.priceUsd() != null && !pair.priceUsd().isBlank()) {
                    try {
                        fallbackPrice = new BigDecimal(pair.priceUsd());
                    } catch (NumberFormatException ignored) {}
                }
                BigDecimal fallbackLiquidity = pair.liquidity() != null ? pair.liquidity().usd() : null;

                return new ResolvedMetadata(symbol, name, 6, logoUri, fallbackPrice, fallbackLiquidity);
            }
        } catch (Exception e) {
            log.debug("Gagal mengambil metadata DexScreener untuk {}: {}", contractAddress, e.getMessage());
        }

        return new ResolvedMetadata(null, null, 6, null, null, null);
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
