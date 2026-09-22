package com.finflow.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.event.DomainEvent;
import com.finflow.common.event.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Consumer group "audit-group": append-only audit trail. A second listener archives every dead-lettered message. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditConsumer {

    private final AuditRepository repository;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    @KafkaListener(topics = {Topics.PAYMENTS, Topics.WALLET_EVENTS, Topics.USER_EVENTS}, groupId = "audit-group")
    public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        DomainEvent event = mapper.readValue(record.value(), DomainEvent.class);
        if (!repository.existsByEventId(event.eventId())) { // idempotent consumer
            AuditRecord row = new AuditRecord();
            row.setEventId(event.eventId());
            row.setKind("EVENT");
            row.setTopic(record.topic());
            row.setEventType(event.type());
            row.setAggregateId(event.aggregateId());
            row.setCorrelationId(event.correlationId());
            row.setPayload(record.value());
            row.setOccurredAt(event.occurredAt());
            row.setRecordedAt(Instant.now());
            repository.save(row);
            meters.counter("audit_events_recorded_total", "topic", record.topic()).increment();
        }
        ack.acknowledge();
    }

    @KafkaListener(topicPattern = ".*\\.DLT", groupId = "audit-dlt-group")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String id = "dlt-" + record.topic() + "-" + record.partition() + "-" + record.offset();
            if (!repository.existsByEventId(id)) {
                AuditRecord row = new AuditRecord();
                row.setEventId(id);
                row.setKind("DEAD_LETTER");
                row.setTopic(header(record, "kafka_dlt-original-topic", record.topic()));
                row.setEventType("UNKNOWN");
                row.setPayload(record.value() == null ? "" : record.value());
                row.setErrorMessage(header(record, "kafka_dlt-exception-message", "unknown"));
                row.setRecordedAt(Instant.now());
                try {
                    DomainEvent e = mapper.readValue(record.value(), DomainEvent.class);
                    row.setEventType(e.type());
                    row.setAggregateId(e.aggregateId());
                    row.setCorrelationId(e.correlationId());
                    row.setOccurredAt(e.occurredAt());
                } catch (Exception ignored) {
                    // payload was not a DomainEvent - keep it raw
                }
                repository.save(row);
                log.warn("Archived dead-lettered message {}", id);
            }
        } catch (Exception e) {
            log.error("Could not archive dead letter", e); // never throw from the DLT listener (would loop)
        }
        ack.acknowledge();
    }

    private static String header(ConsumerRecord<?, ?> record, String name, String fallback) {
        Header h = record.headers().lastHeader(name);
        return h == null ? fallback : new String(h.value(), StandardCharsets.UTF_8);
    }
}
