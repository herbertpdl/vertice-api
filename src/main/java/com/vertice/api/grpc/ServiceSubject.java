package com.vertice.api.grpc;

import java.util.Arrays;
import java.util.Optional;

/**
 * The non-user subjects vertice-bff mints tokens for (vertice-bff {@code src/lib/jwt.ts},
 * {@code src/ops/ops-token.ts}). Each is allowed on a handful of RPCs only; see CLAUDE.md.
 */
public enum ServiceSubject {
    /** The running BFF, for calls with no user behind them: login, register, password setup. */
    BFF("service:bff"),
    /** The BFF's ops scripts (e.g. issuing setup tokens to every client). */
    OPS("service:ops");

    /** The {@code role} claim every service token carries. */
    public static final String ROLE = "SERVICE";

    private final String subject;

    ServiceSubject(String subject) {
        this.subject = subject;
    }

    public String subject() {
        return subject;
    }

    public static Optional<ServiceSubject> fromSubject(String subject) {
        return Arrays.stream(values()).filter(s -> s.subject.equals(subject)).findFirst();
    }
}
