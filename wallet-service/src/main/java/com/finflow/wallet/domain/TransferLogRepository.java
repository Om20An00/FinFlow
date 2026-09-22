package com.finflow.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferLogRepository extends JpaRepository<TransferLog, String> {
}
