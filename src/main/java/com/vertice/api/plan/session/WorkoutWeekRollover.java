package com.vertice.api.plan.session;

import com.vertice.api.common.time.BusinessDates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Closes the week (PRD R27, R28): every Monday just after midnight in São Paulo, sessions left in
 * progress from earlier weeks are finished when the client ticked at least one set and deleted
 * when not. Start and list do the same for their own client, so a late or missed run never shows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkoutWeekRollover {

    private final WorkoutSessionService workoutSessionService;
    private final Clock clock;

    @Scheduled(cron = "0 5 0 * * MON", zone = BusinessDates.ZONE_ID)
    public void rollOver() {
        int touched = workoutSessionService.rollOver(BusinessDates.currentWeekStart(clock));
        log.info("Weekly rollover closed {} workout sessions left in progress", touched);
    }
}
