package com.vertice.api.plan.session;

/**
 * Where a workout session stands. A workout/client/week has at most one {@link #IN_PROGRESS} and
 * at most one {@link #COMPLETED} session; finishing a redo of a completed workout turns the
 * earlier one {@link #SUPERSEDED} (kept with its feedback, out of "last time" and progress).
 */
public enum WorkoutLogStatus {
    IN_PROGRESS,
    COMPLETED,
    SUPERSEDED
}
