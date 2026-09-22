package com.finflow.outbox;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EntityScan("com.finflow")
@EnableJpaRepositories("com.finflow")
public class OutboxConfig {
}
