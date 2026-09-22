package com.finflow.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditRepository repository;

    @GetMapping
    public List<AuditRecord> latest(@RequestParam(defaultValue = "40") int limit) {
        return repository.findByKindOrderByIdDesc("EVENT", PageRequest.of(0, Math.min(limit, 200)));
    }

    @GetMapping("/dead-letters")
    public List<AuditRecord> deadLetters() {
        return repository.findByKindOrderByIdDesc("DEAD_LETTER", PageRequest.of(0, 50));
    }

    /** Follow one request across services: every event carries the correlation id created at the gateway/UI. */
    @GetMapping("/trace/{correlationId}")
    public List<AuditRecord> trace(@PathVariable String correlationId) {
        return repository.findByCorrelationIdOrderByIdAsc(correlationId);
    }
}
