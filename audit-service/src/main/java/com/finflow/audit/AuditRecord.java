package com.finflow.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    private String kind; // EVENT | DEAD_LETTER

    private String topic;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "aggregate_id")
    private String aggregateId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(nullable = false)
    private String payload;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
