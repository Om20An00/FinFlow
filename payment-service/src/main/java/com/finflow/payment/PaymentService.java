package com.finflow.payment;

import com.finflow.common.event.DomainEvent;
import com.finflow.common.event.Topics;
import com.finflow.grpc.wallet.TransferRequest;
import com.finflow.grpc.wallet.TransferResponse;
import com.finflow.outbox.OutboxService;
import com.finflow.payment.api.CreatePaymentRequest;
import com.finflow.payment.domain.Payment;
import com.finflow.payment.domain.PaymentRepository;
import com.finflow.payment.domain.PaymentStatus;
import com.finflow.payment.grpc.WalletClient;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payment orchestration:
 *   1. idempotency check (Redis fast path, PostgreSQL unique constraint as the real guarantee)
 *   2. persist PENDING payment + PaymentCreated outbox event   (one DB transaction)
 *   3. gRPC call to Wallet Service (no DB transaction held open across the network call)
 *   4. persist final status + PaymentCompleted/PaymentFailed outbox event (one DB transaction)
 * If the wallet call is ambiguous (timeout / unavailable) the payment stays PENDING and a reconciler retries it;
 * that is safe because Wallet Service's Transfer is idempotent on payment id.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final int MAX_RECONCILE_ATTEMPTS = 5;

    public record CreateResult(Payment payment, boolean replayed) {
    }

    private final PaymentRepository repository;
    private final TransactionTemplate tx;
    private final OutboxService outbox;
    private final StringRedisTemplate redis;
    private final WalletClient wallet;
    private final MeterRegistry meters;

    public CreateResult create(Jwt jwt, CreatePaymentRequest request, String idempotencyKey) {
        String senderId = jwt.getSubject();
        if (senderId.equals(request.toUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot pay yourself");
        }

        String cacheKey = "idem:" + senderId + ":" + idempotencyKey;
        Payment existing = lookup(senderId, idempotencyKey, cacheKey);
        if (existing != null) {
            meters.counter("payment_idempotent_replays_total").increment();
            return new CreateResult(existing, true);
        }

        Payment created;
        try {
            created = tx.execute(status -> {
                Instant now = Instant.now();
                Payment p = repository.saveAndFlush(Payment.builder()
                        .senderId(senderId)
                        .senderName(jwt.getClaimAsString("preferred_username"))
                        .receiverId(request.toUserId())
                        .amount(request.amount())
                        .currency("INR")
                        .note(request.note())
                        .status(PaymentStatus.PENDING)
                        .idempotencyKey(idempotencyKey)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                outbox.publish(Topics.PAYMENTS, p.getId().toString(), event("PaymentCreated", p, null));
                return p;
            });
        } catch (DataIntegrityViolationException duplicate) {
            // two identical requests raced: the unique (sender_id, idempotency_key) constraint let only one through
            Payment winner = repository.findBySenderIdAndIdempotencyKey(senderId, idempotencyKey).orElseThrow(() -> duplicate);
            meters.counter("payment_idempotent_replays_total").increment();
            return new CreateResult(winner, true);
        }

        try {
            redis.opsForValue().set(cacheKey, created.getId().toString(), Duration.ofHours(24));
        } catch (Exception e) {
            log.debug("Idempotency cache write skipped: {}", e.toString());
        }
        return new CreateResult(settle(created), false);
    }

    /** Calls Wallet Service via gRPC and records the outcome. */
    Payment settle(Payment p) {
        TransferRequest request = TransferRequest.newBuilder()
                .setPaymentId(p.getId().toString())
                .setFromUserId(p.getSenderId())
                .setToUserId(p.getReceiverId())
                .setAmount(p.getAmount().toPlainString())
                .setCurrency(p.getCurrency())
                .build();
        try {
            TransferResponse response = wallet.transfer(request);
            return finalizePayment(p.getId(), response);
        } catch (StatusRuntimeException e) {
            log.warn("Wallet gRPC call failed for payment {} ({}); leaving it PENDING for the reconciler", p.getId(), e.getStatus());
            meters.counter("payment_wallet_unavailable_total").increment();
            return repository.findById(p.getId()).orElse(p);
        } catch (ObjectOptimisticLockingFailureException e) {
            return repository.findById(p.getId()).orElse(p); // somebody else finished it first
        }
    }

    Payment finalizePayment(UUID id, TransferResponse response) {
        Payment result = tx.execute(status -> {
            Payment p = repository.findById(id).orElseThrow();
            if (p.getStatus() != PaymentStatus.PENDING) {
                return p;
            }
            if (!response.getFromName().isBlank()) {
                p.setSenderName(response.getFromName());
            }
            if (!response.getToName().isBlank()) {
                p.setReceiverName(response.getToName());
            }
            if (response.getSuccess()) {
                p.setStatus(PaymentStatus.COMPLETED);
            } else {
                p.setStatus(PaymentStatus.FAILED);
                p.setFailureReason(response.getCode());
            }
            p.setUpdatedAt(Instant.now());
            repository.save(p);
            outbox.publish(Topics.PAYMENTS, p.getId().toString(),
                    event(response.getSuccess() ? "PaymentCompleted" : "PaymentFailed", p, p.getFailureReason()));
            return p;
        });
        meters.counter(result.getStatus() == PaymentStatus.COMPLETED ? "payment_success_total" : "payment_failure_total").increment();
        return result;
    }

    /** Safety net for ambiguous gRPC failures. Idempotent Transfer makes retrying always safe. */
    @Scheduled(fixedDelay = 10_000, initialDelay = 20_000)
    public void reconcilePending() {
        List<Payment> stuck = repository.findTop20ByStatusAndCreatedAtBeforeOrderByCreatedAt(
                PaymentStatus.PENDING, Instant.now().minusSeconds(10));
        for (Payment p : stuck) {
            try {
                if (p.getAttempts() >= MAX_RECONCILE_ATTEMPTS) {
                    finalizePayment(p.getId(), TransferResponse.newBuilder().setSuccess(false).setCode("WALLET_UNAVAILABLE").build());
                    continue;
                }
                repository.incrementAttempts(p.getId());
                log.info("Reconciling pending payment {} (attempt {})", p.getId(), p.getAttempts() + 1);
                settle(p);
            } catch (Exception e) {
                log.warn("Reconciliation of {} failed: {}", p.getId(), e.toString());
            }
        }
    }

    public List<Payment> recent(String userId) {
        return repository.findTop30BySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId);
    }

    public Payment get(UUID id, String userId) {
        Payment p = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (!p.getSenderId().equals(userId) && !p.getReceiverId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }
        return p;
    }

    public BigDecimal totalReceived(String merchantId) {
        BigDecimal sum = repository.sumReceived(merchantId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    public long countReceived(String merchantId) {
        return repository.countByReceiverIdAndStatus(merchantId, PaymentStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- helpers

    private Payment lookup(String senderId, String key, String cacheKey) {
        try {
            String cachedId = redis.opsForValue().get(cacheKey);
            if (cachedId != null) {
                Payment p = repository.findById(UUID.fromString(cachedId)).orElse(null);
                if (p != null) {
                    return p;
                }
            }
        } catch (Exception e) {
            log.debug("Idempotency cache read skipped: {}", e.toString());
        }
        return repository.findBySenderIdAndIdempotencyKey(senderId, key).orElse(null);
    }

    private DomainEvent event(String type, Payment p, String reason) {
        return DomainEvent.of(type, p.getId().toString(),
                "paymentId", p.getId().toString(),
                "senderId", p.getSenderId(), "senderName", p.getSenderName(),
                "receiverId", p.getReceiverId(), "receiverName", p.getReceiverName(),
                "amount", p.getAmount(), "currency", p.getCurrency(),
                "note", p.getNote(), "status", p.getStatus().name(), "reason", reason);
    }
}
