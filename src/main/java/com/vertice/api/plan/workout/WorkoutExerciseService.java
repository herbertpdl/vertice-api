package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseRequest;
import com.vertice.api.generated.grpc.plan.v1.WorkoutExerciseResponse;
import com.vertice.api.plan.exercise.Exercise;
import com.vertice.api.plan.exercise.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutExerciseService {

    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutExerciseMapper workoutExerciseMapper;
    private final WorkoutRepository workoutRepository;
    private final ExerciseRepository exerciseRepository;

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
        Exercise exercise = exerciseRepository.findById(request.getExerciseId())
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", request.getExerciseId()));
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

    public void deleteWorkoutExercise(Long id) {
        WorkoutExercise workoutExercise = findByIdOrThrow(id);
        workoutExerciseRepository.delete(workoutExercise);
    }

    private WorkoutExercise findByIdOrThrow(Long id) {
        return workoutExerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutExercise", id));
    }
}
