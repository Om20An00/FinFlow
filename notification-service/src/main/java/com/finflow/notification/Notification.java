package com.finflow.notification;

import java.time.Instant;

public record Notification(String id, String type, String title, String message, String correlationId, Instant createdAt) {
}
