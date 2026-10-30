package com.vertice.api.plan.exercise;

import com.vertice.api.common.exception.ExerciseInUseException;
import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.ListExercisesResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.plan.workout.WorkoutExerciseRepository;
import com.vertice.api.user.Role;
import com.vertice.api.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Role rules (spec §0 D5): trainers see the starter set plus their own exercises and manage only
 * their own; admins see the starter set only and manage nothing; clients cannot list and can only
 * open an exercise used in one of their own plans. The starter set is never changed or deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExerciseService {

    public static final int DEFAULT_PAGE_SIZE = 30;
    public static final int MAX_PAGE_SIZE = 50;

    private final ExerciseRepository exerciseRepository;
    private final MuscleGroupRepository muscleGroupRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final UserRepository userRepository;
    private final ExerciseMapper exerciseMapper;

    @Transactional(readOnly = true)
    public List<MuscleGroupResponse> listMuscleGroups() {
        return muscleGroupRepository.findAllByOrderByIdAsc().stream()
                .map(exerciseMapper::toMuscleGroupResponse)
                .toList();
    }

    /**
     * One page of the catalog: never the whole of it, however it is filtered (the starter set alone
     * is 199 rows and a trainer's own exercises are unbounded).
     *
     * <p>Passing the caller's own id for an ADMIN too is deliberate: an admin never owns an exercise
     * (creating one is trainer-only), so the query collapses to the starter set.
     *
     * @param muscleGroupId {@code 0} = no group filter
     * @param search        name substring, case-insensitive; blank = no filter
     * @param pageSize      already bounded by the caller (1 to {@link #MAX_PAGE_SIZE})
     * @param offset        rows to skip, from the previous page's token; {@code 0} for the first page
     */
    @Transactional(readOnly = true)
    public ListExercisesResponse listExercises(CallerIdentity caller, long muscleGroupId, String search,
                                               int pageSize, int offset) {
        caller.requireRole("list exercises", Role.TRAINER, Role.ADMIN);
        if (muscleGroupId != 0 && !muscleGroupRepository.existsById(muscleGroupId)) {
            throw new ResourceNotFoundException("MuscleGroup", muscleGroupId);
        }
        // One row past the page tells whether another page exists without a count query.
        List<Exercise> rows = exerciseRepository.findVisible(
                caller.userId(), muscleGroupId, escapeLike(search.strip()), pageSize + 1, offset);
        boolean hasNext = rows.size() > pageSize;
        ListExercisesResponse.Builder response = ListExercisesResponse.newBuilder();
        (hasNext ? rows.subList(0, pageSize) : rows).forEach(e -> response.addExercises(exerciseMapper.toResponse(e)));
        if (hasNext) {
            response.setNextPageToken(String.valueOf(offset + pageSize));
        }
        return response.build();
    }

    @Transactional(readOnly = true)
    public ExerciseResponse getExercise(CallerIdentity caller, Long id) {
        Exercise exercise = findByIdOrThrow(id);
        boolean visible = caller.role() == Role.CLIENT
                ? workoutExerciseRepository.existsByExerciseIdAndWorkout_TrainingPlan_Client_Id(id, caller.userId())
                : exercise.isVisibleTo(caller);
        if (!visible) {
            throw PermissionDeniedException.noAccess("exercise", id);
        }
        return exerciseMapper.toResponse(exercise);
    }

    public ExerciseResponse createExercise(CallerIdentity caller, ExerciseRequest request) {
        caller.requireRole("create exercises", Role.TRAINER);
        List<MuscleGroup> groups = resolveMuscleGroups(request.getMuscleGroupIdsList());
        Exercise exercise = exerciseMapper.toEntity(request);
        exercise.setOwner(userRepository.getReferenceById(caller.userId()));
        exercise.addMuscleGroups(groups);
        return exerciseMapper.toResponse(exerciseRepository.save(exercise));
    }

    public ExerciseResponse updateExercise(CallerIdentity caller, Long id, ExerciseRequest request) {
        caller.requireRole("change exercises", Role.TRAINER);
        Exercise exercise = findOwnedOrThrow(caller, id, "changed");
        List<MuscleGroup> groups = resolveMuscleGroups(request.getMuscleGroupIdsList());
        exerciseMapper.updateEntityFromRequest(request, exercise);
        replaceMuscleGroups(exercise, groups);
        return exerciseMapper.toResponse(exerciseRepository.save(exercise));
    }

    public void deleteExercise(CallerIdentity caller, Long id) {
        caller.requireRole("delete exercises", Role.TRAINER);
        Exercise exercise = findOwnedOrThrow(caller, id, "deleted");
        // Checked up front so the workout_exercises FK never surfaces as a raw UNKNOWN error.
        if (workoutExerciseRepository.existsByExerciseId(id)) {
            throw new ExerciseInUseException(id);
        }
        exerciseRepository.delete(exercise);
    }

    /** Existence first, then starter-set immutability, then ownership. */
    private Exercise findOwnedOrThrow(CallerIdentity caller, Long id, String verb) {
        Exercise exercise = findByIdOrThrow(id);
        if (exercise.isStarter()) {
            throw PermissionDeniedException.starterSet(id, verb);
        }
        if (!exercise.isOwnedBy(caller)) {
            throw PermissionDeniedException.noAccess("exercise", id);
        }
        return exercise;
    }

    /**
     * Hibernate flushes new inserts before orphan deletes, so re-linking a group the exercise
     * already has would trip {@code uq_exercise_muscle_groups_exercise_group}. Flushing the
     * removal first avoids that.
     */
    private void replaceMuscleGroups(Exercise exercise, List<MuscleGroup> groups) {
        exercise.getMuscleGroups().clear();
        exerciseRepository.flush();
        exercise.addMuscleGroups(groups);
    }

    /** Makes a user-typed {@code %}, {@code _} or backslash match literally (the query escapes with a backslash). */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Exercise findByIdOrThrow(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", id));
    }

    /**
     * De-duplicates {@code ids} keeping request order and loads them; the lowest unknown id is
     * reported. Emptiness is checked by the controller.
     */
    private List<MuscleGroup> resolveMuscleGroups(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        Map<Long, MuscleGroup> found = muscleGroupRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(MuscleGroup::getId, Function.identity()));
        distinctIds.stream()
                .filter(id -> !found.containsKey(id))
                .min(Long::compare)
                .ifPresent(unknown -> {
                    throw new ConstraintViolationException(
                            "muscleGroupIds: unknown muscle group %d".formatted(unknown), Set.of());
                });
        return distinctIds.stream().map(found::get).toList();
    }
}
