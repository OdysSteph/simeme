package com.steph.simeme.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DexScreenerTokenResponse(
        List<PairData> pairs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PairData(
            String chainId,
            String priceUsd,
            BaseToken baseToken,
            Liquidity liquidity,
            PairInfo info
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaseToken(
            String address,
            String name,
            String symbol
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Liquidity(
            BigDecimal usd
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PairInfo(
            String imageUrl
    ) {}
}
