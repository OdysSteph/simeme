package com.steph.simeme.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LivePriceTickDto(
        Instant serverTime,
        List<TokenTick> ticks
) {
    public record TokenTick(
            String contractAddress,
            String symbol,
            String name,
            BigDecimal priceUsd,
            BigDecimal liquidityUsd
    ) {}
}