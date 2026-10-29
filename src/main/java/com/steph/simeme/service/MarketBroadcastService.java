package com.steph.simeme.service;

import com.steph.simeme.dto.LivePriceTickDto;
import com.steph.simeme.dto.PortfolioResponse;
import com.steph.simeme.entity.Token;
import com.steph.simeme.entity.User;
import com.steph.simeme.repository.TokenRepository;
import com.steph.simeme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketBroadcastService {

    // Komponen inti Spring untuk menembakkan data ke saluran WebSocket
    private final SimpMessagingTemplate messagingTemplate;
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PriceCacheService priceCacheService;
    private final TradingService tradingService;

    /**
     * Memancarkan harga terbaru ke seluruh pengguna yang membuka aplikasi
     */
    public void broadcastMarketPrices(List<Token> featuredTokens) {
        List<LivePriceTickDto.TokenTick> ticks = featuredTokens.stream()
                .map(token -> {
                    var state = priceCacheService.getMarketState(token.getContractAddress());
                    return new LivePriceTickDto.TokenTick(
                            token.getContractAddress(),
                            token.getSymbol(),
                            token.getName(),
                            state.map(PriceCacheService.TokenMarketState::priceUsd)
                                    .orElse(token.getLastKnownPrice() != null ? token.getLastKnownPrice() : BigDecimal.ZERO),
                            state.map(PriceCacheService.TokenMarketState::liquidityUsd).orElse(BigDecimal.ZERO)
                    );
                })
                .toList();

        LivePriceTickDto payload = new LivePriceTickDto(Instant.now(), ticks);

        // 1. Pancarkan daftar harga ke saluran publik: /topic/prices
        messagingTemplate.convertAndSend("/topic/prices", payload);

        // 2. Pancarkan update PnL portofolio ke masing-masing user
        broadcastPortfolios();
    }

    /**
     * Memancarkan pembaruan saldo & Unrealized PnL secara real-time
     */
    public void broadcastPortfolios() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            broadcastSingleUserPortfolio(user.getUsername());
        }
    }

    /**
     * Dipanggil saat harga berubah ATAU tepat setelah user mengklik tombol BUY/SELL
     */
    public void broadcastSingleUserPortfolio(String username) {
        try {
            PortfolioResponse portfolio = tradingService.getUserPortfolio(username);
            messagingTemplate.convertAndSend("/topic/portfolio/" + username, portfolio);
        } catch (Exception e) {
            log.error("Gagal memancarkan portofolio untuk user {}: {}", username, e.getMessage());
        }
    }
}