package com.finflow.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}
