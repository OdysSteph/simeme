package com.steph.simeme.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class PriceCacheService {

    // Record internal untuk menyimpan state harga & likuiditas suatu koin di RAM
    public record TokenMarketState(
            BigDecimal priceUsd,
            BigDecimal liquidityUsd,
            Instant updatedAt
    ) {}

    // ConcurrentHashMap aman diakses bersamaan oleh ratusan user (Thread-Safe)
    // Key: Contract Address, Value: TokenMarketState
    private final Map<String, TokenMarketState> priceCache = new ConcurrentHashMap<>();

    // Menyimpan atau memperbarui harga di memori
    public void updatePrice(String contractAddress, BigDecimal price, BigDecimal liquidity) {
        BigDecimal safeLiquidity = (liquidity != null && liquidity.compareTo(BigDecimal.ZERO) > 0)
                ? liquidity
                : new BigDecimal("100000"); // Default liquidity jika null atau 0
        priceCache.put(contractAddress, new TokenMarketState(price, safeLiquidity, Instant.now()));
    }

    // Mengambil harga suatu koin secara instan dari memori
    public Optional<TokenMarketState> getMarketState(String contractAddress) {
        return Optional.ofNullable(priceCache.get(contractAddress));
    }

    // Mengambil seluruh daftar harga di memori
    public Map<String, TokenMarketState> getAllPrices() {
        return priceCache;
    }
}