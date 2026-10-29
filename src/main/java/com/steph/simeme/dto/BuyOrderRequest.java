package com.steph.simeme.dto;

import java.math.BigDecimal;

public record BuyOrderRequest(
        String username,        // Contoh: "trader_satu"
        String contractAddress, // CA koin yang mau dibeli
        BigDecimal amountUsd    // Berapa USD yang mau dibelanjakan (misal: 100)
) {}