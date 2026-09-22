package com.finflow.payment.api;

import com.finflow.payment.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    public record Settlement(BigDecimal totalReceived, long paymentsReceived) {
    }

    private final PaymentService service;

    /**
     * 201 completed | 202 pending (wallet unavailable, will be reconciled) | 422 failed (business rule) |
     * 200 + Idempotent-Replayed=true when the same Idempotency-Key was already used.
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentView> create(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody CreatePaymentRequest request,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        String idempotencyKey = (key == null || key.isBlank()) ? UUID.randomUUID().toString() : key;
        PaymentService.CreateResult result = service.create(jwt, request, idempotencyKey);
        HttpStatus status;
        if (result.replayed()) {
            status = HttpStatus.OK;
        } else {
            status = switch (result.payment().getStatus()) {
                case COMPLETED -> HttpStatus.CREATED;
                case PENDING -> HttpStatus.ACCEPTED;
                case FAILED -> HttpStatus.valueOf(422);
            };
        }
        return ResponseEntity.status(status)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(PaymentView.of(result.payment(), jwt.getSubject()));
    }

    @GetMapping
    public List<PaymentView> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.recent(jwt.getSubject()).stream().map(p -> PaymentView.of(p, jwt.getSubject())).toList();
    }

    @GetMapping("/{id}")
    public PaymentView one(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PaymentView.of(service.get(id, jwt.getSubject()), jwt.getSubject());
    }

    @GetMapping("/merchant/settlement")
    @PreAuthorize("hasRole('MERCHANT')")
    public Settlement settlement(@AuthenticationPrincipal Jwt jwt) {
        return new Settlement(service.totalReceived(jwt.getSubject()), service.countReceived(jwt.getSubject()));
    }
}
