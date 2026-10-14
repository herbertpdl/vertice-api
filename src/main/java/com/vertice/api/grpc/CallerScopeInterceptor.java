package com.vertice.api.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Gives every call an empty slot for the caller {@link CallerIdentityResolver} resolves, so the
 * user lookup behind it runs once per RPC however many times a controller asks who is calling.
 * The slot lives and dies with the call; nothing is shared across requests.
 */
@Component
@GlobalServerInterceptor
class CallerScopeInterceptor implements ServerInterceptor {

    static final Context.Key<AtomicReference<Caller>> RESOLVED_CALLER = Context.key("vertice.resolved-caller");

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        Context context = Context.current().withValue(RESOLVED_CALLER, new AtomicReference<>());
        return Contexts.interceptCall(context, call, headers, next);
    }
}
