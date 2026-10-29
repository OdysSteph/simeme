package com.steph.simeme.controller;

import com.steph.simeme.dto.BuyOrderRequest;
import com.steph.simeme.dto.PortfolioResponse;
import com.steph.simeme.dto.SellOrderRequest;
import com.steph.simeme.entity.TradeHistory;
import com.steph.simeme.service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradingController {

    private final TradingService tradingService;

    // 1. Endpoint untuk melihat saldo Wallet + daftar koin yang di-hold beserta persentase profit/loss
    @GetMapping("/portfolio/{username}")
    public ResponseEntity<PortfolioResponse> getPortfolio(@PathVariable String username) {
        return ResponseEntity.ok(tradingService.getUserPortfolio(username));
    }

    // 2. Endpoint untuk eksekusi Beli Koin
    @PostMapping("/buy")
    public ResponseEntity<?> buyToken(@RequestBody BuyOrderRequest request) {
        try {
            TradeHistory trade = tradingService.executeBuy(request);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "side", "BUY",
                    "symbol", trade.getToken().getSymbol(),
                    "spentUsd", trade.getAmountUsd(),
                    "tokensReceived", trade.getTokenAmount(),
                    "executionPrice", trade.getExecutionPrice(),
                    "feeUsd", trade.getFeeUsd()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }

    // 3. Endpoint untuk eksekusi Jual Koin
    @PostMapping("/sell")
    public ResponseEntity<?> sellToken(@RequestBody SellOrderRequest request) {
        try {
            TradeHistory trade = tradingService.executeSell(request);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "side", "SELL",
                    "symbol", trade.getToken().getSymbol(),
                    "netUsdReceived", trade.getAmountUsd(),
                    "tokensSold", trade.getTokenAmount(),
                    "executionPrice", trade.getExecutionPrice(),
                    "realizedPnlUsd", trade.getRealizedPnlUsd()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }
}