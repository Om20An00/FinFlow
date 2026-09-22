package com.finflow.common.kafka;

import com.finflow.common.event.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Shared Kafka behaviour: topic creation, and a consumer error strategy of
 * retry 3 times (1s apart) and then publish the record to "<topic>.DLT" (dead-letter queue).
 */
@Configuration
public class KafkaCommonConfig {

    @Bean
    NewTopic paymentsTopic() {
        return TopicBuilder.name(Topics.PAYMENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic walletEventsTopic() {
        return TopicBuilder.name(Topics.WALLET_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic userEventsTopic() {
        return TopicBuilder.name(Topics.USER_EVENTS).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic paymentsDlt() {
        return TopicBuilder.name(Topics.PAYMENTS + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic walletEventsDlt() {
        return TopicBuilder.name(Topics.WALLET_EVENTS + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic userEventsDlt() {
        return TopicBuilder.name(Topics.USER_EVENTS + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template, MeterRegistry meters) {
        DeadLetterPublishingRecoverer dlq = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            meters.counter("kafka_dead_letters_total", "topic", record.topic()).increment();
            dlq.accept(record, ex);
        };
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        handler.setCommitRecovered(true); // needed because listeners use manual acknowledgment
        return handler;
    }
}
