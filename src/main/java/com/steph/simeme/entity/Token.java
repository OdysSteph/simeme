package com.steph.simeme.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Token {

    // Kita gunakan Contract Address langsung sebagai Primary Key karena sifatnya pasti unik
    @Id
    @Column(name = "contract_address", length = 100)
    private String contractAddress;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer decimals;

    @Column(name = "logo_uri")
    private String logoUri;

    @Column(nullable = false, length = 20)
    private String chain; // Contoh: "solana"

    // Penanda apakah koin ini masuk ke daftar 10 koin utama di beranda aplikasi
    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured;

    // Harga terakhir yang tersimpan (sebagai cadangan sebelum WebSocket aktif)
    @Column(name = "last_known_price", precision = 38, scale = 18)
    private BigDecimal lastKnownPrice;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}