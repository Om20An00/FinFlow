package com.finflow.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox table and pushes unpublished rows to Kafka (at-least-once delivery).
 * If Kafka is down the rows simply stay in the table and are retried - nothing is lost.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;

    @Scheduled(fixedDelayString = "${finflow.outbox.poll-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = repository.lockNextBatch(50);
        for (OutboxEvent event : batch) {
            try {
                kafka.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.setPublishedAt(Instant.now());
                meters.counter("outbox_published_total", "topic", event.getTopic()).increment();
            } catch (Exception e) {
                log.warn("Outbox relay could not publish event {} to {} - will retry: {}", event.getId(), event.getTopic(), e.toString());
                break;
            }
        }
    }
}
