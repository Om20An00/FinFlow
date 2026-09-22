package com.finflow.payment.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.slf4j.MDC;

/** Copies the current request's correlation id into gRPC metadata so Wallet Service logs can be joined with ours. */
public class CorrelationClientInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> KEY = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions options, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
            @Override
            public void start(Listener<RespT> listener, Metadata headers) {
                String id = MDC.get("correlationId");
                if (id != null) {
                    headers.put(KEY, id);
                }
                super.start(listener, headers);
            }
        };
    }
}
