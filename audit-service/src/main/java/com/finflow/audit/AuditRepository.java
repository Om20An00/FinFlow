package com.finflow.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditRecord, Long> {

    boolean existsByEventId(String eventId);

    List<AuditRecord> findByKindOrderByIdDesc(String kind, Pageable pageable);

    List<AuditRecord> findByCorrelationIdOrderByIdAsc(String correlationId);
}
