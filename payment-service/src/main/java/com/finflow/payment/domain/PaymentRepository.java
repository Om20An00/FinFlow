package com.finflow.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findBySenderIdAndIdempotencyKey(String senderId, String idempotencyKey);

    List<Payment> findTop30BySenderIdOrReceiverIdOrderByCreatedAtDesc(String senderId, String receiverId);

    List<Payment> findTop20ByStatusAndCreatedAtBeforeOrderByCreatedAt(PaymentStatus status, Instant before);

    long countByReceiverIdAndStatus(String receiverId, PaymentStatus status);

    @Query("select sum(p.amount) from Payment p where p.receiverId = :id and p.status = com.finflow.payment.domain.PaymentStatus.COMPLETED")
    BigDecimal sumReceived(@Param("id") String receiverId);

    @Modifying
    @Transactional
    @Query("update Payment p set p.attempts = p.attempts + 1 where p.id = :id")
    void incrementAttempts(@Param("id") UUID id);
}
