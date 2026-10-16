package com.vertice.api.common.time;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Server-set timestamps at the precision Postgres {@code TIMESTAMPTZ} stores (microseconds). On
 * Linux {@link Instant#now()} carries nanoseconds, which Postgres rounds away, so without this the
 * RPC that writes a row returns a different value than every later read of the same row.
 */
public final class DbInstants {

    private DbInstants() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
