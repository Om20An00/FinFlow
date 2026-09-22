package com.finflow.wallet.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletView(String userId, String username, BigDecimal balance, String currency,
                         boolean frozen, Instant updatedAt, String source) {
}
