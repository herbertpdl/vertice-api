package com.vertice.api.plan.workout;

/**
 * Upper bounds on what a workout holds, shared by the per-item RPCs ({@code CreateWorkoutExercise},
 * {@code CreateExerciseSet}) and the nested ones ({@code CreateWorkoutWithExercises},
 * {@code ReplaceWorkoutExercises}), and by {@code RecordSetLog} for logged reps. vertice-bff and
 * vertice-web-react mirror these numbers (P1-09), so change them in all three together.
 */
public final class WorkoutLimits {

    public static final int MAX_EXERCISES_PER_WORKOUT = 20;
    public static final int MAX_SETS_PER_EXERCISE = 10;

    /** A workout exercise's {@code order} and a set's {@code set_number}: far above the caps, so renumbering never hits it. */
    public static final int MAX_POSITION = 1_000;
    public static final int MAX_REPS = 1_000;
    /** One day: a timed set (a long run, say) never gets near it. */
    public static final int MAX_DURATION_SECONDS = 86_400;
    /** Rest after a set or between sets: one hour. */
    public static final int MAX_REST_SECONDS = 3_600;
    /** {@code notes} on workout exercises and sets: {@code VARCHAR(255)}. */
    public static final int MAX_NOTES_LENGTH = 255;

    private WorkoutLimits() {
    }
}
