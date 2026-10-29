package com.steph.simeme.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "positions", uniqueConstraints = {
    // Memastikan 1 user hanya punya 1 baris portofolio untuk tiap token yang sama
    @UniqueConstraint(columnNames = {"user_id", "token_address"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_address", nullable = false)
    private Token token;

    // Jumlah koin yang dimiliki
    @Column(name = "amount_held", nullable = false, precision = 38, scale = 12)
    private BigDecimal amountHeld;

    // Rata-rata harga beli dalam USD
    @Column(name = "average_buy_price", nullable = false, precision = 38, scale = 18)
    private BigDecimal averageBuyPrice;
}