package com.steph.simeme.service;

import com.steph.simeme.dto.BuyOrderRequest;
import com.steph.simeme.dto.PortfolioResponse;
import com.steph.simeme.dto.SellOrderRequest;
import com.steph.simeme.entity.*;
import com.steph.simeme.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TokenRepository tokenRepository;
    private final PositionRepository positionRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final PriceCacheService priceCacheService;

    // Simulasi fee platform 1% (0.01) seperti aplikasi trading meme coin
    private static final BigDecimal TRADING_FEE_RATE = new BigDecimal("0.01");
    // Base slippage 0.3% (0.003)
    private static final BigDecimal BASE_SLIPPAGE = new BigDecimal("0.003");

    /**
     * EKSEKUSI ORDER BELI (BUY)
     */
    @Transactional
    public TradeHistory executeBuy(BuyOrderRequest request) {
        if (request.amountUsd().compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Minimal pembelian adalah $1 USD");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan"));
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Wallet tidak ditemukan"));
        Token token = tokenRepository.findById(request.contractAddress())
                .orElseThrow(() -> new IllegalArgumentException("Token tidak terdaftar di sistem"));

        // 1. Cek apakah saldo USD mencukupi
        if (wallet.getBalanceUsd().compareTo(request.amountUsd()) < 0) {
            throw new IllegalArgumentException("Saldo USD tidak mencukupi! Saldo saat ini: $" + wallet.getBalanceUsd());
        }

        // 2. Ambil harga real-time dari RAM (PriceCacheService)
        var marketState = priceCacheService.getMarketState(token.getContractAddress())
                .orElseThrow(() -> new IllegalStateException("Harga pasar belum tersedia untuk token ini, tunggu beberapa detik."));

        BigDecimal marketPrice = marketState.priceUsd();
        BigDecimal liquidity = marketState.liquidityUsd();

        // 3. Hitung Slippage (Semakin besar order dibanding likuiditas, semakin mahal harga belinya)
        BigDecimal priceImpact = request.amountUsd().divide(liquidity, 8, RoundingMode.HALF_UP);
        BigDecimal totalSlippage = BASE_SLIPPAGE.add(priceImpact);
        BigDecimal executionPrice = marketPrice.multiply(BigDecimal.ONE.add(totalSlippage))
                .setScale(18, RoundingMode.HALF_UP);

        // 4. Potong Fee 1% dari nominal USD yang dimasukkan
        BigDecimal feeUsd = request.amountUsd().multiply(TRADING_FEE_RATE).setScale(8, RoundingMode.HALF_UP);
        BigDecimal netUsdForTokens = request.amountUsd().subtract(feeUsd);

        // 5. Hitung jumlah koin yang didapat
        BigDecimal tokensReceived = netUsdForTokens.divide(executionPrice, 12, RoundingMode.DOWN);

        // 6. Kurangi saldo Wallet pengguna
        wallet.setBalanceUsd(wallet.getBalanceUsd().subtract(request.amountUsd()));
        walletRepository.save(wallet);

        // 7. Perbarui atau buat Position (Portofolio) baru
        Position position = positionRepository.findByUserIdAndToken_ContractAddress(user.getId(), token.getContractAddress())
                .orElse(null);

        if (position == null) {
            position = Position.builder()
                    .user(user)
                    .token(token)
                    .amountHeld(tokensReceived)
                    .averageBuyPrice(executionPrice)
                    .build();
        } else {
            // Rumus Average Buy Price baru: ((JumlahLama * HargaLama) + NominalUsdBaru) / (JumlahLama + JumlahBaru)
            BigDecimal oldCost = position.getAmountHeld().multiply(position.getAverageBuyPrice());
            BigDecimal newTotalCost = oldCost.add(netUsdForTokens);
            BigDecimal newTotalAmount = position.getAmountHeld().add(tokensReceived);
            BigDecimal newAvgPrice = newTotalCost.divide(newTotalAmount, 18, RoundingMode.HALF_UP);

            position.setAmountHeld(newTotalAmount);
            position.setAverageBuyPrice(newAvgPrice);
        }
        positionRepository.save(position);

        // 8. Catat struk transaksi di TradeHistory
        TradeHistory history = TradeHistory.builder()
                .user(user)
                .token(token)
                .side(TradeHistory.TradeSide.BUY)
                .amountUsd(request.amountUsd())
                .tokenAmount(tokensReceived)
                .executionPrice(executionPrice)
                .feeUsd(feeUsd)
                .realizedPnlUsd(null) // Belum ada realized PnL saat baru membeli
                .executedAt(LocalDateTime.now())
                .build();

        log.info("BUY EXECUTED: {} membeli {} {} di harga ${}", user.getUsername(), tokensReceived, token.getSymbol(), executionPrice);
        return tradeHistoryRepository.save(history);
    }

    /**
     * EKSEKUSI ORDER JUAL (SELL BERDASARKAN PERSENTASE: 1% - 100%)
     */
    @Transactional
    public TradeHistory executeSell(SellOrderRequest request) {
        if (request.sellPercentage().compareTo(BigDecimal.ZERO) <= 0 ||
            request.sellPercentage().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Persentase jual harus di antara 1 hingga 100");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan"));
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Wallet tidak ditemukan"));
        Position position = positionRepository.findByUserIdAndToken_ContractAddress(user.getId(), request.contractAddress())
                .orElseThrow(() -> new IllegalArgumentException("Kamu tidak memiliki koin ini di portofolio!"));

        // 1. Hitung berapa jumlah koin yang dijual berdasarkan persentase
        BigDecimal fraction = request.sellPercentage().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        BigDecimal tokensToSell = position.getAmountHeld().multiply(fraction).setScale(12, RoundingMode.DOWN);

        if (tokensToSell.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Jumlah koin yang dijual terlalu kecil");
        }

        // 2. Ambil harga pasar real-time dari RAM
        var marketState = priceCacheService.getMarketState(request.contractAddress())
                .orElseThrow(() -> new IllegalStateException("Harga pasar belum tersedia"));

        BigDecimal marketPrice = marketState.priceUsd();
        BigDecimal executionPrice = marketPrice.multiply(BigDecimal.ONE.subtract(BASE_SLIPPAGE))
                .setScale(18, RoundingMode.HALF_UP);

        // 3. Hitung nilai USD kotor, potong fee 1%, dapatkan nilai USD bersih
        BigDecimal grossUsd = tokensToSell.multiply(executionPrice).setScale(8, RoundingMode.DOWN);
        BigDecimal feeUsd = grossUsd.multiply(TRADING_FEE_RATE).setScale(8, RoundingMode.HALF_UP);
        BigDecimal netUsdReceived = grossUsd.subtract(feeUsd);

        // 4. Hitung Realized Profit / Loss (PnL) dibanding modal rata-rata beli (Average Buy Price)
        BigDecimal costBasisUsd = tokensToSell.multiply(position.getAverageBuyPrice()).setScale(8, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = netUsdReceived.subtract(costBasisUsd);

        // 5. Tambahkan uang hasil penjualan ke Wallet pengguna
        wallet.setBalanceUsd(wallet.getBalanceUsd().add(netUsdReceived));
        walletRepository.save(wallet);

        // 6. Kurangi jumlah koin di portofolio (Jika dijual 100%, hapus baris posisi dari DB)
        BigDecimal remainingTokens = position.getAmountHeld().subtract(tokensToSell);
        if (request.sellPercentage().compareTo(new BigDecimal("100")) == 0 || remainingTokens.compareTo(new BigDecimal("0.000001")) <= 0) {
            positionRepository.delete(position);
        } else {
            position.setAmountHeld(remainingTokens);
            positionRepository.save(position);
        }

        // 7. Simpan riwayat penjualan beserta Realized PnL
        TradeHistory history = TradeHistory.builder()
                .user(user)
                .token(position.getToken())
                .side(TradeHistory.TradeSide.SELL)
                .amountUsd(netUsdReceived)
                .tokenAmount(tokensToSell)
                .executionPrice(executionPrice)
                .feeUsd(feeUsd)
                .realizedPnlUsd(realizedPnl)
                .executedAt(LocalDateTime.now())
                .build();

        log.info("SELL EXECUTED: {} menjual {}% {} dan menerima ${} (PnL: ${})",
                user.getUsername(), request.sellPercentage(), position.getToken().getSymbol(), netUsdReceived, realizedPnl);
        return tradeHistoryRepository.save(history);
    }

    /**
     * LIHAT PORTOFOLIO & HITUNG UNREALIZED PnL SECARA REAL-TIME
     */
    @Transactional(readOnly = true)
    public PortfolioResponse getUserPortfolio(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan"));
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Wallet tidak ditemukan"));

        List<Position> positions = positionRepository.findByUserId(user.getId());
        List<PortfolioResponse.PositionItem> items = new ArrayList<>();
        BigDecimal totalHoldingsValueUsd = BigDecimal.ZERO;

        for (Position pos : positions) {
            BigDecimal currentPrice = priceCacheService.getMarketState(pos.getToken().getContractAddress())
                    .map(PriceCacheService.TokenMarketState::priceUsd)
                    .orElse(pos.getAverageBuyPrice());

            BigDecimal currentValueUsd = pos.getAmountHeld().multiply(currentPrice).setScale(8, RoundingMode.HALF_UP);
            BigDecimal costBasisUsd = pos.getAmountHeld().multiply(pos.getAverageBuyPrice()).setScale(8, RoundingMode.HALF_UP);
            BigDecimal unrealizedPnlUsd = currentValueUsd.subtract(costBasisUsd);

            BigDecimal pnlPercentage = BigDecimal.ZERO;
            if (costBasisUsd.compareTo(BigDecimal.ZERO) > 0) {
                pnlPercentage = unrealizedPnlUsd.divide(costBasisUsd, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            totalHoldingsValueUsd = totalHoldingsValueUsd.add(currentValueUsd);

            items.add(new PortfolioResponse.PositionItem(
                    pos.getToken().getContractAddress(),
                    pos.getToken().getSymbol(),
                    pos.getAmountHeld(),
                    pos.getAverageBuyPrice(),
                    currentPrice,
                    currentValueUsd,
                    unrealizedPnlUsd,
                    pnlPercentage
            ));
        }

        return new PortfolioResponse(
                user.getUsername(),
                wallet.getBalanceUsd(),
                wallet.getBalanceUsd().add(totalHoldingsValueUsd),
                items
        );
    }
}