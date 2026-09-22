package com.finflow.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.event.DomainEvent;
import com.finflow.common.event.Topics;
import com.finflow.outbox.OutboxService;
import com.finflow.wallet.api.dto.TxView;
import com.finflow.wallet.api.dto.WalletView;
import com.finflow.wallet.domain.LedgerEntry;
import com.finflow.wallet.domain.LedgerRepository;
import com.finflow.wallet.domain.TransferLog;
import com.finflow.wallet.domain.TransferLogRepository;
import com.finflow.wallet.domain.Wallet;
import com.finflow.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final WalletRepository wallets;
    private final LedgerRepository ledger;
    private final TransferLogRepository transfers;
    private final OutboxService outbox;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public record TransferResult(boolean success, String code, String message,
                                 BigDecimal fromBalance, BigDecimal toBalance, String fromName, String toName) {
    }

    /**
     * The core money movement. One database transaction: debit + credit + ledger + idempotency record + outbox events.
     * Concurrent modifications are detected by @Version and surface as ObjectOptimisticLockingFailureException
     * (the gRPC layer retries).
     */
    @Transactional
    public TransferResult transfer(String paymentId, String fromId, String toId, BigDecimal amount, String currency) {
        if (amount.signum() <= 0 || fromId.equals(toId)) {
            return fail("INVALID", "Amount must be positive and sender/receiver must differ", null, null);
        }
        Wallet from = wallets.findById(fromId).orElse(null);
        Wallet to = wallets.findById(toId).orElse(null);

        if (transfers.existsById(paymentId)) { // idempotent replay of an already processed payment
            return from != null && to != null ? ok("ALREADY_PROCESSED", from, to)
                    : fail("ALREADY_PROCESSED", "Already processed", from, to);
        }
        if (from == null || to == null) {
            return fail("WALLET_NOT_FOUND", "Sender or receiver wallet does not exist", from, to);
        }
        if (from.isFrozen() || to.isFrozen()) {
            return fail("WALLET_FROZEN", "A wallet involved in this payment is frozen", from, to);
        }
        if (from.getBalance().compareTo(amount) < 0) {
            return fail("INSUFFICIENT_FUNDS", "Insufficient balance", from, to);
        }

        from.debit(amount);
        to.credit(amount);
        ledger.save(entry(from, paymentId, "DEBIT", amount, to.getUsername()));
        ledger.save(entry(to, paymentId, "CREDIT", amount, from.getUsername()));
        transfers.save(new TransferLog(paymentId, fromId, toId, amount, Instant.now()));

        outbox.publish(Topics.WALLET_EVENTS, fromId, DomainEvent.of("WalletDebited", fromId,
                "userId", fromId, "amount", amount, "paymentId", paymentId,
                "balanceAfter", from.getBalance(), "counterparty", to.getUsername()));
        outbox.publish(Topics.WALLET_EVENTS, toId, DomainEvent.of("WalletCredited", toId,
                "userId", toId, "amount", amount, "paymentId", paymentId,
                "balanceAfter", to.getBalance(), "counterparty", from.getUsername()));

        evictAfterCommit(fromId, toId);
        return ok("OK", from, to);
    }

    /** Read path: Redis first (15s TTL), PostgreSQL remains the source of truth. */
    public WalletView view(String userId) {
        String key = cacheKey(userId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                WalletView v = mapper.readValue(cached, WalletView.class);
                return new WalletView(v.userId(), v.username(), v.balance(), v.currency(), v.frozen(), v.updatedAt(), "redis");
            }
        } catch (Exception e) {
            log.debug("Ignoring cache read problem: {}", e.toString());
        }
        Wallet w = wallets.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not created yet - try again in a moment"));
        WalletView view = toView(w, "postgres");
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(view), CACHE_TTL);
        } catch (Exception e) {
            log.debug("Ignoring cache write problem: {}", e.toString());
        }
        return view;
    }

    @Transactional(readOnly = true)
    public List<TxView> history(String userId) {
        return ledger.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(e -> new TxView(e.getId().toString(), e.getDirection(), e.getAmount(), e.getBalanceAfter(),
                        e.getCounterparty(), e.getPaymentId(), e.getCreatedAt()))
                .toList();
    }

    @Transactional
    public WalletView topUp(String userId, BigDecimal amount) {
        Wallet w = wallets.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
        if (w.isFrozen()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wallet is frozen");
        }
        w.credit(amount);
        ledger.save(entry(w, null, "CREDIT", amount, "Top-up"));
        outbox.publish(Topics.WALLET_EVENTS, userId, DomainEvent.of("WalletToppedUp", userId,
                "userId", userId, "amount", amount, "balanceAfter", w.getBalance()));
        evictAfterCommit(userId);
        return toView(w, "postgres");
    }

    @Transactional
    public WalletView setFrozen(String userId, boolean frozen, String actor) {
        Wallet w = wallets.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));
        w.setFrozen(frozen);
        w.setUpdatedAt(Instant.now());
        outbox.publish(Topics.WALLET_EVENTS, userId, DomainEvent.of(frozen ? "WalletFrozen" : "WalletUnfrozen", userId,
                "userId", userId, "username", w.getUsername(), "actor", actor));
        evictAfterCommit(userId);
        return toView(w, "postgres");
    }

    @Transactional(readOnly = true)
    public List<WalletView> listAll() {
        return wallets.findAllByOrderByUsernameAsc().stream().map(w -> toView(w, "postgres")).toList();
    }

    /** Reaction to the UserRegistered event: every new customer gets a wallet with a demo welcome balance. */
    @Transactional
    public void createIfAbsent(String userId, String username) {
        if (wallets.existsById(userId)) {
            return;
        }
        Wallet w = new Wallet();
        w.setUserId(userId);
        w.setUsername(username);
        w.setBalance(new BigDecimal("1000.00"));
        w.setCurrency("INR");
        w.setUpdatedAt(Instant.now());
        wallets.save(w);
        ledger.save(entry(w, null, "CREDIT", new BigDecimal("1000.00"), "Welcome bonus"));
        outbox.publish(Topics.WALLET_EVENTS, userId, DomainEvent.of("WalletCreated", userId,
                "userId", userId, "username", username));
    }

    // ---------------------------------------------------------------- helpers

    private LedgerEntry entry(Wallet w, String paymentId, String direction, BigDecimal amount, String counterparty) {
        LedgerEntry e = new LedgerEntry();
        e.setUserId(w.getUserId());
        e.setPaymentId(paymentId);
        e.setDirection(direction);
        e.setAmount(amount);
        e.setBalanceAfter(w.getBalance());
        e.setCounterparty(counterparty);
        e.setCreatedAt(Instant.now());
        return e;
    }

    private WalletView toView(Wallet w, String source) {
        return new WalletView(w.getUserId(), w.getUsername(), w.getBalance(), w.getCurrency(), w.isFrozen(), w.getUpdatedAt(), source);
    }

    private TransferResult ok(String code, Wallet from, Wallet to) {
        return new TransferResult(true, code, "OK", from.getBalance(), to.getBalance(), from.getUsername(), to.getUsername());
    }

    private TransferResult fail(String code, String message, Wallet from, Wallet to) {
        return new TransferResult(false, code, message,
                from == null ? null : from.getBalance(), to == null ? null : to.getBalance(),
                from == null ? null : from.getUsername(), to == null ? null : to.getUsername());
    }

    private static String cacheKey(String userId) {
        return "wallet:view:" + userId;
    }

    private void evictAfterCommit(String... userIds) {
        Runnable evict = () -> {
            try {
                for (String id : userIds) {
                    redis.delete(cacheKey(id));
                }
            } catch (Exception e) {
                log.warn("Cache eviction failed (TTL will expire it): {}", e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
