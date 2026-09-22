package com.finflow.wallet.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.event.DomainEvent;
import com.finflow.common.event.Topics;
import com.finflow.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventsConsumer {

    private final ObjectMapper mapper;
    private final WalletService walletService;

    @KafkaListener(topics = Topics.USER_EVENTS, groupId = "wallet-user-group")
    public void onUserEvent(String payload, Acknowledgment ack) throws Exception {
        DomainEvent event = mapper.readValue(payload, DomainEvent.class);
        if ("UserRegistered".equals(event.type())) {
            walletService.createIfAbsent(event.str("userId"), event.str("username"));
            log.info("Wallet provisioned for new user {}", event.str("username"));
        }
        ack.acknowledge();
    }
}
