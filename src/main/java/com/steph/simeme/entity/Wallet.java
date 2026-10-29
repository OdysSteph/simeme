package com.steph.simeme.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Relasi One-to-One: 1 User memiliki 1 Wallet utama
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Saldo USD atau SOL simulasi (misal modal awal diberi $1000 atau 10 SOL)
    // Precision 38, Scale 8 artinya total 38 digit dengan 8 angka di belakang koma
    @Column(name = "balance_usd", nullable = false, precision = 38, scale = 8)
    private BigDecimal balanceUsd;

    // @Version digunakan oleh JPA untuk Optimistic Locking (mencegah saldo bentrok saat klik cepat)
    @Version
    private Long version;
}