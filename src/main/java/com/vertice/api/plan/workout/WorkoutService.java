package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
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

    private Workout findByIdOrThrow(Long id) {
        return workoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workout", id));
    }
}
