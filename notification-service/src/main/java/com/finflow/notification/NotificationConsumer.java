package com.finflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.event.DomainEvent;
import com.finflow.common.event.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumer group "notification-group". Demonstrates:
 *  - manual acknowledgment (offset committed only after successful processing)
 *  - idempotent consumption (eventId marker in Redis: a redelivered event is ignored)
 *  - retry + dead-letter queue (configured centrally; note "#poison" in a payment note forces a failure)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    @KafkaListener(topics = {Topics.PAYMENTS, Topics.WALLET_EVENTS, Topics.USER_EVENTS}, groupId = "notification-group")
    public void onEvent(String payload, Acknowledgment ack) throws Exception {
        DomainEvent event = mapper.readValue(payload, DomainEvent.class);
        String marker = "notif:processed:" + event.eventId();
        Boolean first = redis.opsForValue().setIfAbsent(marker, "1", Duration.ofHours(24));
        if (!Boolean.TRUE.equals(first)) {
            log.info("Duplicate event {} ({}) ignored", event.eventId(), event.type());
            meters.counter("notification_duplicates_total").increment();
            ack.acknowledge();
            return;
        }
        try {
            handle(event);
        } catch (Exception e) {
            redis.delete(marker); // allow the retry to process it again
            throw e;
        }
        ack.acknowledge();
    }

    private void handle(DomainEvent e) throws Exception {
        switch (e.type()) {
            case "PaymentCompleted" -> {
                if (e.str("note").toLowerCase().contains("#poison")) {
                    throw new IllegalStateException("Simulated notification provider outage (poison message)");
                }
                String amount = "₹" + e.str("amount");
                push(e.str("senderId"), "PAYMENT_SENT", "Payment sent",
                        "You paid " + amount + " to " + e.str("receiverName"), e);
                push(e.str("receiverId"), "PAYMENT_RECEIVED", "Payment received",
                        "You received " + amount + " from " + e.str("senderName"), e);
            }
            case "PaymentFailed" -> push(e.str("senderId"), "PAYMENT_FAILED", "Payment failed",
                    "Your payment of ₹" + e.str("amount") + " to " + e.str("receiverName") + " failed: " + e.str("reason"), e);
            case "WalletFrozen" -> push(e.str("userId"), "WALLET_FROZEN", "Wallet frozen",
                    "Your wallet was frozen by " + e.str("actor") + ". Payments are blocked.", e);
            case "WalletUnfrozen" -> push(e.str("userId"), "WALLET_UNFROZEN", "Wallet unfrozen",
                    "Your wallet was re-activated by " + e.str("actor") + ".", e);
            case "UserRegistered" -> push(e.str("userId"), "WELCOME", "Welcome to FinFlow",
                    "Hi " + e.str("username") + ", your account is ready.", e);
            default -> { /* other event types are not user-facing */ }
        }
    }

    private void push(String userId, String type, String title, String message, DomainEvent e) throws Exception {
        if (userId.isBlank()) {
            return;
        }
        Notification n = new Notification(UUID.randomUUID().toString(), type, title, message, e.correlationId(), Instant.now());
        String key = "notif:user:" + userId;
        redis.opsForList().leftPush(key, mapper.writeValueAsString(n));
        redis.opsForList().trim(key, 0, 49);
        meters.counter("notifications_sent_total", "type", type).increment();
        log.info("Notification for {}: {}", userId, message);
    }
}
