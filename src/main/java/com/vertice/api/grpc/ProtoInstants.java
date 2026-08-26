package com.vertice.api.grpc;

import org.mapstruct.Named;

import java.time.Instant;

/**
 * Server-set timestamps (e.g. when a session was started/completed) cross the wire as ISO-8601
 * instant strings, response-only — nothing in this codebase accepts a client-supplied instant
 * back, so unlike {@link ProtoDates}/{@link ProtoDecimals} there's no string-to-{@link Instant}
 * direction to provide.
 */
public final class ProtoInstants {

    private ProtoInstants() {
    }

    @Named("instantToString")
    public static String instantToString(Instant value) {
        return value == null ? "" : value.toString();
    }
}
