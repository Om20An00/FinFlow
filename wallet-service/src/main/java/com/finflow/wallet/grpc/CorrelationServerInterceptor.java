package com.finflow.wallet.grpc;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.MDC;

/** Carries the correlation id from the calling service into this service's logs. */
public class CorrelationServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> KEY = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String id = headers.get(KEY);
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onHalfClose() {
                if (id != null) {
                    MDC.put("correlationId", id);
                }
                try {
                    super.onHalfClose();
                } finally {
                    MDC.remove("correlationId");
                }
            }
        };
    }
}
