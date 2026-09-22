package com.finflow.wallet.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Starts/stops the gRPC server together with the Spring context. */
@Component
@Slf4j
public class GrpcServerLifecycle implements SmartLifecycle {

    private final WalletGrpcService service;
    private final int port;
    private Server server;

    public GrpcServerLifecycle(WalletGrpcService service, @Value("${finflow.grpc.port:9092}") int port) {
        this.service = service;
        this.port = port;
    }

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(port)
                    .addService(ServerInterceptors.intercept(service, new CorrelationServerInterceptor()))
                    .build()
                    .start();
            log.info("gRPC server listening on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }
}
