package com.steph.simeme.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.steph.simeme.dto.AddTokenRequest;
import com.steph.simeme.entity.Token;
import com.steph.simeme.repository.TokenRepository;
import com.steph.simeme.service.BirdeyeMarketService;
import com.steph.simeme.service.PriceCacheService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenRepository tokenRepository;
    private final PriceCacheService priceCacheService;
    private final BirdeyeMarketService birdeyeMarketService;

    // DTO khusus untuk respons ke Frontend agar rapi dan memuat harga live dari RAM
    public record TokenMarketResponse(
            String contractAddress,
            String symbol,
            String name,
            String chain,
            Integer decimals,
            String logoUri,
            BigDecimal livePriceUsd,
            BigDecimal liquidityUsd,
            Instant priceUpdatedAt
    ) {}

    @GetMapping({"", "/featured"})
    public List<TokenMarketResponse> getFeaturedTokens() {
        return tokenRepository.findByIsFeaturedTrue().stream()
                .map(this::mapToMarketResponse)
                .toList();
    }

    @GetMapping("/{address}")
    public ResponseEntity<?> getTokenByAddress(@PathVariable String address) {
        try {
            Token token = tokenRepository.findById(address.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Token tidak ditemukan: " + address));

            if (priceCacheService.getMarketState(token.getContractAddress()).isEmpty()) {
                birdeyeMarketService.fetchAndCacheSingleTokenPrice(token);
            }

            return ResponseEntity.ok(mapToMarketResponse(token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Endpoint untuk menambahkan token baru hanya dengan paste Contract Address (CA).
     * Mendukung POST /api/tokens maupun POST /api/tokens/add
     */
    @PostMapping({"", "/add"})
    public ResponseEntity<?> addTokenByCa(@RequestBody AddTokenRequest request) {
        try {
            Token savedToken = birdeyeMarketService.addTokenByContractAddress(request);
            return ResponseEntity.ok(mapToMarketResponse(savedToken));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Alternatif penambahan token langsung via path parameter: POST /api/tokens/{address}
     */
    @PostMapping("/{address}")
    public ResponseEntity<?> addTokenByPathCa(@PathVariable String address) {
        try {
            Token savedToken = birdeyeMarketService.addTokenByContractAddress(
                    new AddTokenRequest(address, null, null, null, null)
            );
            return ResponseEntity.ok(mapToMarketResponse(savedToken));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    private TokenMarketResponse mapToMarketResponse(Token token) {
        var marketState = priceCacheService.getMarketState(token.getContractAddress());

        return new TokenMarketResponse(
                token.getContractAddress(),
                token.getSymbol(),
                token.getName(),
                token.getChain(),
                token.getDecimals(),
                token.getLogoUri(),
                marketState.map(PriceCacheService.TokenMarketState::priceUsd).orElse(token.getLastKnownPrice()),
                marketState.map(PriceCacheService.TokenMarketState::liquidityUsd).orElse(BigDecimal.ZERO),
                marketState.map(PriceCacheService.TokenMarketState::updatedAt).orElse(null)
        );
    }
}
