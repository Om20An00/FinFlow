package com.finflow.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * SKIP LOCKED lets several replicas of the same service run the relay concurrently
     * without publishing the same row twice.
     */
    @Query(value = "SELECT * FROM outbox_event WHERE published_at IS NULL ORDER BY created_at LIMIT :n FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("n") int n);
}
