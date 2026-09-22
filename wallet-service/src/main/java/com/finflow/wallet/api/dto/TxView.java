package com.finflow.wallet.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TxView(String id, String direction, BigDecimal amount, BigDecimal balanceAfter,
                     String counterparty, String paymentId, Instant createdAt) {
}
