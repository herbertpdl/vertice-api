package com.vertice.api.common.time;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Business dates are calendar days in São Paulo (P2-02): the zone the product lives in, so a
 * week starts on Monday there regardless of the server's zone.
 */
public final class BusinessDates {

    public static final String ZONE_ID = "America/Sao_Paulo";
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private BusinessDates() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** The Monday that starts the current business week. */
    public static LocalDate currentWeekStart(Clock clock) {
        return today(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
