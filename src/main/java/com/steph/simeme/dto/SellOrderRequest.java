package com.steph.simeme.dto;

import java.math.BigDecimal;

public record SellOrderRequest(
        String username,          // Contoh: "trader_satu"
        String contractAddress,   // CA koin yang mau dijual
        BigDecimal sellPercentage // Persentase koin yang dijual (1 sampai 100)
) {}