package com.steph.simeme.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        String username,
        BigDecimal walletBalanceUsd,
        BigDecimal totalPortfolioValueUsd,
        List<PositionItem> holdings
) {
    public record PositionItem(
            String contractAddress,
            String symbol,
            BigDecimal amountHeld,
            BigDecimal averageBuyPrice,
            BigDecimal currentPrice,
            BigDecimal currentValueUsd,
            BigDecimal unrealizedPnlUsd,
            BigDecimal unrealizedPnlPercentage
    ) {}
}