package com.vertice.api.plan.exercise;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseMapper exerciseMapper;

    @Transactional(readOnly = true)
    public List<ExerciseResponse> listExercises() {
        return exerciseRepository.findAll().stream()
                .map(exerciseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExerciseResponse getExercise(Long id) {
        return exerciseMapper.toResponse(findByIdOrThrow(id));
    }

    public ExerciseResponse createExercise(ExerciseRequest request) {
        Exercise exercise = exerciseMapper.toEntity(request);
        return exerciseMapper.toResponse(exerciseRepository.save(exercise));
    }

    public ExerciseResponse updateExercise(Long id, ExerciseRequest request) {
        Exercise exercise = findByIdOrThrow(id);
        exerciseMapper.updateEntityFromRequest(request, exercise);
        return exerciseMapper.toResponse(exerciseRepository.save(exercise));
    }

    public void deleteExercise(Long id) {
        Exercise exercise = findByIdOrThrow(id);
        exerciseRepository.delete(exercise);
    }

    private Exercise findByIdOrThrow(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", id));
    }
}
