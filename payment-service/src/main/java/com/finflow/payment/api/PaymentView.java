package com.finflow.payment.api;

import com.finflow.payment.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentView(String id, String senderId, String senderName, String receiverId, String receiverName,
                          BigDecimal amount, String currency, String note, String status, String failureReason,
                          String direction, Instant createdAt) {

    public static PaymentView of(Payment p, String me) {
        return new PaymentView(p.getId().toString(), p.getSenderId(), p.getSenderName(), p.getReceiverId(),
                p.getReceiverName(), p.getAmount(), p.getCurrency(), p.getNote(), p.getStatus().name(),
                p.getFailureReason(), p.getSenderId().equals(me) ? "SENT" : "RECEIVED", p.getCreatedAt());
    }
}
