package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.HasRecordedDataException;
import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseResponse;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import com.vertice.api.plan.session.SetLogRepository;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.vertice.api.plan.workout.WorkoutLimits.MAX_EXERCISES_PER_WORKOUT;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutExerciseService {

    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutExerciseMapper workoutExerciseMapper;
    private final WorkoutRepository workoutRepository;
    private final ExerciseRepository exerciseRepository;
    private final SetLogRepository setLogRepository;

    @Transactional(readOnly = true)
    public List<WorkoutExerciseResponse> listWorkoutExercises(Long workoutId) {
        return workoutExerciseRepository.findByWorkoutId(workoutId).stream()
                .map(workoutExerciseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkoutExerciseResponse getWorkoutExercise(Long id) {
        return workoutExerciseMapper.toResponse(findByIdOrThrow(id));
    }

    public WorkoutExerciseResponse createWorkoutExercise(WorkoutExerciseCreateRequest request) {
        Workout workout = workoutRepository.findById(request.getWorkoutId())
                .orElseThrow(() -> new ResourceNotFoundException("Workout", request.getWorkoutId()));
        // Same cap and message as the nested RPCs (WorkoutController). Two concurrent creates can
        // both pass the count and overshoot by one each; the cap is a product bound, not an invariant.
        if (workoutExerciseRepository.countByWorkoutId(workout.getId()) >= MAX_EXERCISES_PER_WORKOUT) {
            throw new ConstraintViolationException(
                    "exercises: must not exceed %d entries".formatted(MAX_EXERCISES_PER_WORKOUT), Set.of());
        }
        Exercise exercise = exerciseRepository.findById(request.getExerciseId())
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", request.getExerciseId()));
        // Spec exercise-starter-catalog PR9 (R17): another trainer's private exercise never goes in.
        if (!exercise.isVisibleToTrainer(workout.getTrainingPlan().getTrainer().getId())) {
            throw PermissionDeniedException.noAccess("exercise", exercise.getId());
        }
        WorkoutExercise workoutExercise = workoutExerciseMapper.toEntity(request);
        workoutExercise.setWorkout(workout);
        workoutExercise.setExercise(exercise);
        return workoutExerciseMapper.toResponse(workoutExerciseRepository.save(workoutExercise));
    }

    public WorkoutExerciseResponse updateWorkoutExercise(Long id, WorkoutExerciseRequest request) {
        WorkoutExercise workoutExercise = findByIdOrThrow(id);
        workoutExerciseMapper.updateEntityFromRequest(request, workoutExercise);
        return workoutExerciseMapper.toResponse(workoutExerciseRepository.save(workoutExercise));
    }

    /** Its sets go with it (JPA cascade), so it is refused once any of them has a logged result. */
    public void deleteWorkoutExercise(Long id) {
        WorkoutExercise workoutExercise = findByIdOrThrow(id);
        if (setLogRepository.existsByExerciseSet_WorkoutExercise_Id(id)) {
            throw new HasRecordedDataException("WorkoutExercise", id, "logged sets");
        }
        workoutExerciseRepository.delete(workoutExercise);
    }

    private WorkoutExercise findByIdOrThrow(Long id) {
        return workoutExerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutExercise", id));
    }
}
