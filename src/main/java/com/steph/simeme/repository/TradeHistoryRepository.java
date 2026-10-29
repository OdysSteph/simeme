package com.steph.simeme.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.steph.simeme.entity.TradeHistory;

@Repository
public interface TradeHistoryRepository extends JpaRepository<TradeHistory, UUID> {
    // Mengambil riwayat transaksi user diurutkan dari yang paling baru
    List<TradeHistory> findByUserIdOrderByExecutedAtDesc(UUID userId);
}