package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.HasRecordedDataException;
import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.exception.WorkoutExerciseHasRecordedDataException;
import com.vertice.api.generated.grpc.plan.v1.CloneWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.CreateWorkoutWithExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetEntry;
import com.vertice.api.generated.grpc.plan.v1.ReplaceWorkoutExercisesRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseEntry;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.plan.session.SetLog;
import com.vertice.api.plan.session.SetLogRepository;
import com.vertice.api.plan.session.WorkoutLogRepository;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutService {

    private final WorkoutRepository workoutRepository;
    private final WorkoutMapper workoutMapper;
    private final TrainingPlanRepository trainingPlanRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutExerciseEntryMapper workoutExerciseEntryMapper;
    private final ExerciseRepository exerciseRepository;
    private final SetLogRepository setLogRepository;
    private final WorkoutLogRepository workoutLogRepository;

    @Transactional(readOnly = true)
    public List<WorkoutResponse> listWorkouts(Long trainingPlanId) {
        return workoutRepository.findByTrainingPlanId(trainingPlanId).stream()
                .map(workoutMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkoutResponse getWorkout(Long id) {
        return workoutMapper.toResponse(findByIdOrThrow(id));
    }

    public WorkoutResponse createWorkout(WorkoutCreateRequest request) {
        TrainingPlan trainingPlan = trainingPlanRepository.findById(request.getTrainingPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainingPlan", request.getTrainingPlanId()));
        Workout workout = workoutMapper.toEntity(request);
        workout.setTrainingPlan(trainingPlan);
        return workoutMapper.toResponse(workoutRepository.save(workout));
    }

    public WorkoutResponse updateWorkout(Long id, WorkoutRequest request) {
        Workout workout = findByIdOrThrow(id);
        workoutMapper.updateEntityFromRequest(request, workout);
        return workoutMapper.toResponse(workoutRepository.save(workout));
    }

    /** Refused once a client has a log for it, like {@code TrainingPlanService#deleteTrainingPlan}. */
    public void deleteWorkout(Long id) {
        Workout workout = findByIdOrThrow(id);
        if (workoutLogRepository.existsByWorkoutId(id)) {
            throw new HasRecordedDataException("Workout", id, "workout logs");
        }
        workoutRepository.delete(workout);
    }

    /**
     * Deep-copies the source {@link Workout}'s full {@link WorkoutExercise}/{@link ExerciseSet}
     * tree (new ids throughout, same catalog {@code Exercise} references) into a new
     * {@code Workout} under the target plan. Builds the whole graph in memory and saves once —
     * {@code cascade = CascadeType.ALL} on both {@code Workout#workoutExercises} and
     * {@code WorkoutExercise#exerciseSets} (already there for {@code workout-exercise-sets})
     * persists everything transitively. A trainer only clones between their own plans, so the
     * visibility check can only refuse an ADMIN copying across trainers.
     */
    public WorkoutResponse cloneWorkout(CloneWorkoutRequest request) {
        Workout source = findByIdOrThrow(request.getSourceWorkoutId());
        TrainingPlan targetPlan = trainingPlanRepository.findById(request.getTargetTrainingPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainingPlan", request.getTargetTrainingPlanId()));
        assertVisibleToPlanTrainer(targetPlan,
                source.getWorkoutExercises().stream().map(WorkoutExercise::getExercise).toList());

        Workout clone = new Workout();
        clone.setName(request.getName());
        clone.setDayOfWeek(workoutMapper.mapDayOfWeek(request.getDayOfWeek()));
        clone.setTrainingPlan(targetPlan);

        for (WorkoutExercise sourceWorkoutExercise : source.getWorkoutExercises()) {
            WorkoutExercise cloneWorkoutExercise = new WorkoutExercise();
            cloneWorkoutExercise.setWorkout(clone);
            cloneWorkoutExercise.setExercise(sourceWorkoutExercise.getExercise());
            cloneWorkoutExercise.setOrder(sourceWorkoutExercise.getOrder());
            cloneWorkoutExercise.setRestSecondsBetweenSets(sourceWorkoutExercise.getRestSecondsBetweenSets());
            cloneWorkoutExercise.setNotes(sourceWorkoutExercise.getNotes());

            for (ExerciseSet sourceSet : sourceWorkoutExercise.getExerciseSets()) {
                ExerciseSet cloneSet = new ExerciseSet();
                cloneSet.setWorkoutExercise(cloneWorkoutExercise);
                cloneSet.setSetNumber(sourceSet.getSetNumber());
                cloneSet.setReps(sourceSet.getReps());
                cloneSet.setDurationSeconds(sourceSet.getDurationSeconds());
                cloneSet.setWeight(sourceSet.getWeight());
                cloneSet.setLoadPercentage(sourceSet.getLoadPercentage());
                cloneSet.setStrategy(sourceSet.getStrategy());
                cloneSet.setRestSeconds(sourceSet.getRestSeconds());
                cloneSet.setNotes(sourceSet.getNotes());
                cloneWorkoutExercise.getExerciseSets().add(cloneSet);
            }

            clone.getWorkoutExercises().add(cloneWorkoutExercise);
        }

        return workoutMapper.toResponse(workoutRepository.save(clone));
    }

    /**
     * Creates a {@link Workout} together with its full {@link WorkoutExercise}/{@link ExerciseSet}
     * tree in one call. Builds the whole graph in memory and saves once, same shape as
     * {@link #cloneWorkout} — {@code cascade = CascadeType.ALL} persists it transitively.
     */
    public WorkoutResponse createWorkoutWithExercises(CreateWorkoutWithExercisesRequest request) {
        TrainingPlan trainingPlan = trainingPlanRepository.findById(request.getTrainingPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainingPlan", request.getTrainingPlanId()));

        Workout workout = new Workout();
        workout.setName(request.getName());
        workout.setDayOfWeek(workoutMapper.mapDayOfWeek(request.getDayOfWeek()));
        workout.setTrainingPlan(trainingPlan);
        workout.getWorkoutExercises().addAll(buildWorkoutExerciseTree(workout, request.getExercisesList()));

        return workoutMapper.toResponse(workoutRepository.save(workout));
    }

    /**
     * Replaces an existing {@link Workout}'s entire exercise/set tree in one call. Refuses the
     * whole operation (zero writes) if any {@link SetLog} exists anywhere under the workout's
     * current tree (R12/R13, §0 — this is a "does the workout have any recorded data at all"
     * check, not a diff against the submitted content, since a full replace can't tell a
     * resubmitted entry apart from a brand-new one). The current tree is loaded with one batched
     * fetch (§0/F8) so the cascading delete below doesn't lazy-load each {@code exerciseSets}
     * collection individually. The new tree is built (and its exercises checked) first: those
     * checks are about the request, the recorded-data one about current state (spec
     * exercise-starter-catalog §5.7).
     */
    public WorkoutResponse replaceWorkoutExercises(ReplaceWorkoutExercisesRequest request) {
        Workout workout = findByIdOrThrow(request.getWorkoutId());
        List<WorkoutExercise> newTree = buildWorkoutExerciseTree(workout, request.getExercisesList());

        List<WorkoutExercise> currentTree =
                workoutExerciseRepository.findByWorkoutIdWithExerciseSetsAndExercise(workout.getId());

        List<SetLog> recordedSetLogs = setLogRepository.findByWorkoutId(workout.getId());
        if (!recordedSetLogs.isEmpty()) {
            ExerciseSet blockingSet = recordedSetLogs.getFirst().getExerciseSet();
            Exercise blockingExercise = blockingSet.getWorkoutExercise().getExercise();
            throw new WorkoutExerciseHasRecordedDataException(
                    blockingExercise.getName(), blockingExercise.getId(), blockingSet.getSetNumber());
        }

        workoutExerciseRepository.deleteAll(currentTree);
        workoutExerciseRepository.saveAll(newTree);

        return workoutMapper.toResponse(workout);
    }

    /**
     * Shared by {@link #createWorkoutWithExercises} and {@link #replaceWorkoutExercises}: resolves
     * every {@code exercise_id} with one batch query (rather than one {@code findById} per entry)
     * and builds one {@link WorkoutExercise} per entry / one {@link ExerciseSet} per nested set
     * entry, with {@code order}/{@code setNumber} derived from list position (R4, R5). Every
     * exercise must be visible to the plan's trainer (spec exercise-starter-catalog PR9, R17).
     */
    private List<WorkoutExercise> buildWorkoutExerciseTree(Workout workout, List<WorkoutExerciseEntry> entries) {
        Set<Long> exerciseIds = entries.stream()
                .map(WorkoutExerciseEntry::getExerciseId)
                .collect(Collectors.toSet());
        Map<Long, Exercise> exercisesById = exerciseRepository.findAllById(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
        if (exercisesById.size() < exerciseIds.size()) {
            throw new ConstraintViolationException("exercise_id: one or more referenced exercises do not exist", Set.of());
        }
        assertVisibleToPlanTrainer(workout.getTrainingPlan(), exercisesById.values());

        List<WorkoutExercise> tree = new ArrayList<>();
        int order = 1;
        for (WorkoutExerciseEntry entry : entries) {
            WorkoutExercise workoutExercise = workoutExerciseEntryMapper.toEntity(entry);
            workoutExercise.setWorkout(workout);
            workoutExercise.setExercise(exercisesById.get(entry.getExerciseId()));
            workoutExercise.setOrder(order++);

            int setNumber = 1;
            for (ExerciseSetEntry setEntry : entry.getSetsList()) {
                ExerciseSet exerciseSet = workoutExerciseEntryMapper.toEntity(setEntry);
                exerciseSet.setWorkoutExercise(workoutExercise);
                exerciseSet.setSetNumber(setNumber++);
                workoutExercise.getExerciseSets().add(exerciseSet);
            }
            tree.add(workoutExercise);
        }
        return tree;
    }

    /** The first offender by ascending id is named, so the answer doesn't depend on request order. */
    private static void assertVisibleToPlanTrainer(TrainingPlan plan, Collection<Exercise> exercises) {
        Long trainerId = plan.getTrainer().getId();
        exercises.stream()
                .filter(exercise -> !exercise.isVisibleToTrainer(trainerId))
                .map(Exercise::getId)
                .min(Long::compare)
                .ifPresent(id -> {
                    throw PermissionDeniedException.noAccess("exercise", id);
                });
    }

    private Workout findByIdOrThrow(Long id) {
        return workoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workout", id));
    }
}
