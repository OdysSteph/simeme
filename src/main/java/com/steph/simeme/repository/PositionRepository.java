package com.steph.simeme.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.steph.simeme.entity.Position;

@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> {
    // Mencari semua koin yang sedang di-hold oleh 1 user
    List<Position> findByUserId(UUID userId);

    // Mencari apakah user sudah pernah beli koin tertentu sebelumnya
    Optional<Position> findByUserIdAndToken_ContractAddress(UUID userId, String contractAddress);
}