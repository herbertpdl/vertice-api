package com.vertice.api.common.time;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs (the weekly workout-session rollover). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
