package com.finflow.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One row per processed payment id: this is what makes the gRPC Transfer call idempotent. */
@Entity
@Table(name = "transfer_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferLog {

    @Id
    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "from_user_id")
    private String fromUserId;

    @Column(name = "to_user_id")
    private String toUserId;

    private BigDecimal amount;

    @Column(name = "created_at")
    private Instant createdAt;
}
