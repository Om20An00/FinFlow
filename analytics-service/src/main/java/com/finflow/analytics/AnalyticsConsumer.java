package com.finflow.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.event.DomainEvent;
import com.finflow.common.event.Topics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Consumer group "analytics-group": builds real-time counters in Redis from the payments topic. */
@Component
@RequiredArgsConstructor
public class AnalyticsConsumer {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @KafkaListener(topics = Topics.PAYMENTS, groupId = "analytics-group")
    public void onPaymentEvent(String payload, Acknowledgment ack) throws Exception {
        DomainEvent e = mapper.readValue(payload, DomainEvent.class);
        Boolean first = redis.opsForValue().setIfAbsent("analytics:seen:" + e.eventId(), "1", Duration.ofDays(2));
        if (Boolean.TRUE.equals(first)) { // idempotent: a redelivered event never double counts
            switch (e.type()) {
                case "PaymentCreated" -> redis.opsForValue().increment("analytics:created");
                case "PaymentFailed" -> redis.opsForValue().increment("analytics:failed");
                case "PaymentCompleted" -> {
                    BigDecimal amount = new BigDecimal(e.str("amount"));
                    long cents = amount.movePointRight(2).longValue();
                    String day = LocalDate.ofInstant(e.occurredAt(), ZoneOffset.UTC).toString();
                    redis.opsForValue().increment("analytics:completed");
                    redis.opsForValue().increment("analytics:volume_cents", cents);
                    redis.opsForValue().increment("analytics:daily:count:" + day);
                    redis.opsForValue().increment("analytics:daily:volume:" + day, cents);
                    redis.opsForZSet().incrementScore("analytics:top_senders", e.str("senderName"), amount.doubleValue());
                }
                default -> { }
            }
        }
        ack.acknowledge();
    }
}
