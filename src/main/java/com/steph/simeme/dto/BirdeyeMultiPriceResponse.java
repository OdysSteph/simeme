package com.steph.simeme.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.Map;

// @JsonIgnoreProperties memastikan jika Birdeye menambah field baru di masa depan, aplikasi kita tidak error
@JsonIgnoreProperties(ignoreUnknown = true)
public record BirdeyeMultiPriceResponse(
        boolean success,
        Map<String, PriceData> data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceData(
            BigDecimal value,       // Harga koin dalam USD
            Long updateUnixTime,    // Waktu update terakhir
            BigDecimal liquidity    // Likuiditas (penting untuk simulasi slippage nanti!)
    ) {}
}