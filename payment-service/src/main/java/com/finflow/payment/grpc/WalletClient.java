package com.finflow.payment.grpc;

import com.finflow.grpc.wallet.TransferRequest;
import com.finflow.grpc.wallet.TransferResponse;
import com.finflow.grpc.wallet.WalletLedgerGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client for the Wallet Service.
 * dns:/// target + round_robin means that on Kubernetes (headless service) calls are spread over all wallet pods.
 */
@Component
public class WalletClient {

    private final ManagedChannel channel;

    public WalletClient(@Value("${finflow.wallet.grpc-target:dns:///wallet-service:9092}") String target) {
        this.channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .defaultLoadBalancingPolicy("round_robin")
                .intercept(new CorrelationClientInterceptor())
                .build();
    }

    public TransferResponse transfer(TransferRequest request) {
        return WalletLedgerGrpc.newBlockingStub(channel)
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .transfer(request);
    }

    @PreDestroy
    public void close() {
        channel.shutdown();
    }
}
