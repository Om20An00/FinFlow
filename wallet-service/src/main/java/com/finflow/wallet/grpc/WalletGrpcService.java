package com.finflow.wallet.grpc;

import com.finflow.grpc.wallet.BalanceRequest;
import com.finflow.grpc.wallet.BalanceResponse;
import com.finflow.grpc.wallet.TransferRequest;
import com.finflow.grpc.wallet.TransferResponse;
import com.finflow.grpc.wallet.WalletLedgerGrpc;
import com.finflow.wallet.WalletService;
import com.finflow.wallet.api.dto.WalletView;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletGrpcService extends WalletLedgerGrpc.WalletLedgerImplBase {

    private static final int MAX_ATTEMPTS = 8;

    private final WalletService walletService;
    private final MeterRegistry meters;

    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> observer) {
        Timer.Sample sample = Timer.start(meters);
        String code = "ERROR";
        try {
            BigDecimal amount = new BigDecimal(request.getAmount());
            WalletService.TransferResult result = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    result = walletService.transfer(request.getPaymentId(), request.getFromUserId(),
                            request.getToUserId(), amount, request.getCurrency());
                    break;
                } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
                    // someone else changed the same wallet (or the same payment) at the same moment - retry on fresh data
                    meters.counter("wallet_optimistic_lock_retries_total").increment();
                    log.warn("Concurrent update on transfer {} (attempt {}/{}): {}", request.getPaymentId(), attempt, MAX_ATTEMPTS, e.getClass().getSimpleName());
                    if (attempt == MAX_ATTEMPTS) {
                        throw e;
                    }
                    backoff();
                }
            }
            code = result.code();
            observer.onNext(TransferResponse.newBuilder()
                    .setSuccess(result.success())
                    .setCode(code)
                    .setMessage(nz(result.message()))
                    .setFromBalance(result.fromBalance() == null ? "" : result.fromBalance().toPlainString())
                    .setToBalance(result.toBalance() == null ? "" : result.toBalance().toPlainString())
                    .setFromName(nz(result.fromName()))
                    .setToName(nz(result.toName()))
                    .build());
            observer.onCompleted();
        } catch (NumberFormatException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("amount is not a decimal").asRuntimeException());
        } catch (Exception e) {
            log.error("Transfer {} failed", request.getPaymentId(), e);
            observer.onError(Status.ABORTED.withDescription(String.valueOf(e.getMessage())).asRuntimeException());
        } finally {
            sample.stop(meters.timer("wallet_operation_latency", "op", "transfer", "result", code));
        }
    }

    @Override
    public void getBalance(BalanceRequest request, StreamObserver<BalanceResponse> observer) {
        try {
            WalletView v = walletService.view(request.getUserId());
            observer.onNext(BalanceResponse.newBuilder().setUserId(v.userId()).setBalance(v.balance().toPlainString())
                    .setCurrency(nz(v.currency())).setFrozen(v.frozen()).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(Status.NOT_FOUND.withDescription(String.valueOf(e.getMessage())).asRuntimeException());
        }
    }

    /** Small randomised pause so competing writers do not collide again in lock-step. */
    private static void backoff() {
        try {
            Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(5, 40));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
