package com.steph.simeme.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.steph.simeme.entity.Token;
import com.steph.simeme.repository.TokenRepository;
import com.steph.simeme.service.PriceCacheService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenRepository tokenRepository;
    private final PriceCacheService priceCacheService;

    // DTO khusus untuk respons ke Frontend agar rapi dan memuat harga live dari RAM
    public record TokenMarketResponse(
            String contractAddress,
            String symbol,
            String name,
            String chain,
            BigDecimal livePriceUsd,
            BigDecimal liquidityUsd,
            Instant priceUpdatedAt
    ) {}

    @GetMapping("/featured")
    public List<TokenMarketResponse> getFeaturedTokens() {
        return tokenRepository.findByIsFeaturedTrue().stream()
                .map(this::mapToMarketResponse)
                .toList();
    }

    @GetMapping("/{address}")
    public TokenMarketResponse getTokenByAddress(@PathVariable String address) {
        Token token = tokenRepository.findById(address)
                .orElseThrow(() -> new IllegalArgumentException("Token tidak ditemukan: " + address));
        return mapToMarketResponse(token);
    }

    private TokenMarketResponse mapToMarketResponse(Token token) {
        var marketState = priceCacheService.getMarketState(token.getContractAddress());

        return new TokenMarketResponse(
                token.getContractAddress(),
                token.getSymbol(),
                token.getName(),
                token.getChain(),
                marketState.map(PriceCacheService.TokenMarketState::priceUsd).orElse(token.getLastKnownPrice()),
                marketState.map(PriceCacheService.TokenMarketState::liquidityUsd).orElse(BigDecimal.ZERO),
                marketState.map(PriceCacheService.TokenMarketState::updatedAt).orElse(null)
        );
    }
}