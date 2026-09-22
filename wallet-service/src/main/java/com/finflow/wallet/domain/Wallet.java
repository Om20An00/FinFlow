package com.finflow.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @Column(name = "user_id")
    private String userId;

    private String username;

    @Column(nullable = false)
    private BigDecimal balance;

    private String currency;

    private boolean frozen;

    /** Optimistic locking: two concurrent updates of the same wallet can never both win. */
    @Version
    private Long version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("INSUFFICIENT_FUNDS");
        }
        balance = balance.subtract(amount);
        updatedAt = Instant.now();
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
        updatedAt = Instant.now();
    }
}
