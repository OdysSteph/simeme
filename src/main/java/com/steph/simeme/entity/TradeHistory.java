package com.steph.simeme.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trade_histories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TradeHistory {

    public enum TradeSide {
        BUY, SELL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_address", nullable = false)
    private Token token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeSide side; // BUY atau SELL

    @Column(name = "amount_usd", nullable = false, precision = 38, scale = 8)
    private BigDecimal amountUsd; // Nominal USD kotor dalam transaksi

    @Column(name = "token_amount", nullable = false, precision = 38, scale = 12)
    private BigDecimal tokenAmount; // Jumlah koin yang didapat / dijual

    @Column(name = "execution_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal executionPrice; // Harga eksekusi setelah terkena slippage

    @Column(name = "fee_usd", nullable = false, precision = 38, scale = 8)
    private BigDecimal feeUsd; // Simulasi potongan biaya platform + gas fee

    // Keuntungan/Kerugian bersih yang dikunci (Hanya terisi saat SELL)
    @Column(name = "realized_pnl_usd", precision = 38, scale = 8)
    private BigDecimal realizedPnlUsd;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
}