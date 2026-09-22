package com.finflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper mapper;

    /**
     * MANDATORY: an event can only be recorded inside the same database transaction as the business change.
     * That is the whole point of the transactional outbox pattern.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String key, DomainEvent event) {
        try {
            OutboxEvent row = new OutboxEvent();
            row.setId(UUID.fromString(event.eventId()));
            row.setTopic(topic);
            row.setEventKey(key);
            row.setPayload(mapper.writeValueAsString(event));
            row.setCreatedAt(Instant.now());
            repository.save(row);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise event " + event.type(), e);
        }
    }
}
