package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.CloneWorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutResponse;
import com.vertice.api.plan.TrainingPlan;
import com.vertice.api.plan.TrainingPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutService {

    private final WorkoutRepository workoutRepository;
    private final WorkoutMapper workoutMapper;
    private final TrainingPlanRepository trainingPlanRepository;

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

    public void deleteWorkout(Long id) {
        Workout workout = findByIdOrThrow(id);
        workoutRepository.delete(workout);
    }

    /**
     * Deep-copies the source {@link Workout}'s full {@link WorkoutExercise}/{@link ExerciseSet}
     * tree (new ids throughout, same catalog {@code Exercise} references) into a new
     * {@code Workout} under the target plan. Builds the whole graph in memory and saves once —
     * {@code cascade = CascadeType.ALL} on both {@code Workout#workoutExercises} and
     * {@code WorkoutExercise#exerciseSets} (already there for {@code workout-exercise-sets})
     * persists everything transitively.
     */
    public WorkoutResponse cloneWorkout(CloneWorkoutRequest request) {
        Workout source = findByIdOrThrow(request.getSourceWorkoutId());
        TrainingPlan targetPlan = trainingPlanRepository.findById(request.getTargetTrainingPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainingPlan", request.getTargetTrainingPlanId()));

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

    private Workout findByIdOrThrow(Long id) {
        return workoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workout", id));
    }
}
