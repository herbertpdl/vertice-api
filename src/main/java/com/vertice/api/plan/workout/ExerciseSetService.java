package com.vertice.api.plan.workout;

import com.vertice.api.common.exception.HasRecordedDataException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetRequest;
import com.vertice.api.generated.grpc.plan.v1.ExerciseSetResponse;
import com.vertice.api.plan.session.SetLogRepository;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static com.vertice.api.plan.workout.WorkoutLimits.MAX_SETS_PER_EXERCISE;

@Service
@RequiredArgsConstructor
@Transactional
public class ExerciseSetService {

    private final ExerciseSetRepository exerciseSetRepository;
    private final ExerciseSetMapper exerciseSetMapper;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final SetLogRepository setLogRepository;

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
        // Same cap and message as the nested RPCs; see WorkoutExerciseService#createWorkoutExercise.
        if (exerciseSetRepository.countByWorkoutExerciseId(workoutExercise.getId()) >= MAX_SETS_PER_EXERCISE) {
            throw new ConstraintViolationException(
                    "sets: must not exceed %d entries".formatted(MAX_SETS_PER_EXERCISE), Set.of());
        }
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
        if (setLogRepository.existsByExerciseSetId(id)) {
            throw new HasRecordedDataException("ExerciseSet", id, "logged results");
        }
        exerciseSetRepository.delete(exerciseSet);
    }

    private ExerciseSet findByIdOrThrow(Long id) {
        return exerciseSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExerciseSet", id));
    }
}
