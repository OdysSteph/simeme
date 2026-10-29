package com.steph.simeme.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BirdeyePriceResponse(
        boolean success,
        PriceData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceData(
            BigDecimal value,           // Harga token dalam USD
            Long updateUnixTime,        // Waktu update terakhir (epoch detik)
            String updateHumanTime,     // Waktu update format ISO
            BigDecimal priceInNative,   // Harga dalam native token (SOL)
            BigDecimal priceChange24h,  // Perubahan harga 24 jam (%)
            BigDecimal liquidity        // Likuiditas dalam USD
    ) {}
}
