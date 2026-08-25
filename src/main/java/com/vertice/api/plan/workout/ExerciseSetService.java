package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExerciseSetService {

    private final ExerciseSetRepository exerciseSetRepository;
    private final ExerciseSetMapper exerciseSetMapper;
    private final WorkoutExerciseRepository workoutExerciseRepository;

    @Transactional(readOnly = true)
    public List<ExerciseSetResponse> listExerciseSets(Long workoutExerciseId) {
        return exerciseSetRepository.findByWorkoutExerciseId(workoutExerciseId).stream()
                .map(exerciseSetMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExerciseSetResponse getExerciseSet(Long id) {
        return exerciseSetMapper.toResponse(findByIdOrThrow(id));
    }

    public ExerciseSetResponse createExerciseSet(ExerciseSetCreateRequest request) {
        WorkoutExercise workoutExercise = workoutExerciseRepository.findById(request.getWorkoutExerciseId())
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutExercise", request.getWorkoutExerciseId()));
        ExerciseSet exerciseSet = exerciseSetMapper.toEntity(request);
        exerciseSet.setWorkoutExercise(workoutExercise);
        return exerciseSetMapper.toResponse(exerciseSetRepository.save(exerciseSet));
    }

    public ExerciseSetResponse updateExerciseSet(Long id, ExerciseSetRequest request) {
        ExerciseSet exerciseSet = findByIdOrThrow(id);
        exerciseSetMapper.updateEntityFromRequest(request, exerciseSet);
        return exerciseSetMapper.toResponse(exerciseSetRepository.save(exerciseSet));
    }

    public void deleteExerciseSet(Long id) {
        ExerciseSet exerciseSet = findByIdOrThrow(id);
        exerciseSetRepository.delete(exerciseSet);
    }

    private ExerciseSet findByIdOrThrow(Long id) {
        return exerciseSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExerciseSet", id));
    }
}
