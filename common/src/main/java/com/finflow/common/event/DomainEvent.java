package com.finflow.common.event;

import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope for every event that travels over Kafka.
 * eventId makes consumers idempotent, correlationId ties the event back to the originating HTTP request.
 */
public record DomainEvent(String eventId,
                          String type,
                          String aggregateId,
                          String correlationId,
                          Instant occurredAt,
                          Map<String, Object> data) {

    /** kv = key1, value1, key2, value2 ... (null values are skipped, BigDecimal is kept as a plain string). */
    public static DomainEvent of(String type, String aggregateId, Object... kv) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v == null) {
                continue;
            }
            if (v instanceof BigDecimal b) {
                v = b.toPlainString();
            } else if (v instanceof Instant t) {
                v = t.toString();
            }
            data.put(String.valueOf(kv[i]), v);
        }
        return new DomainEvent(UUID.randomUUID().toString(), type, aggregateId, MDC.get("correlationId"), Instant.now(), data);
    }

    public String str(String key) {
        Object v = data == null ? null : data.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
