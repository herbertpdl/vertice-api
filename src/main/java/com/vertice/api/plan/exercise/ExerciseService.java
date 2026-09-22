package com.vertice.api.plan.exercise;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final MuscleGroupRepository muscleGroupRepository;
    private final ExerciseMapper exerciseMapper;

    @Transactional(readOnly = true)
    public List<MuscleGroupResponse> listMuscleGroups() {
        return muscleGroupRepository.findAllByOrderByIdAsc().stream()
                .map(exerciseMapper::toMuscleGroupResponse)
                .toList();
    }

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
        List<MuscleGroup> groups = resolveMuscleGroups(request.getMuscleGroupIdsList());
        Exercise exercise = exerciseMapper.toEntity(request);
        exercise.addMuscleGroups(groups);
        return exerciseMapper.toResponse(exerciseRepository.save(exercise));
    }

    public ExerciseResponse updateExercise(Long id, ExerciseRequest request) {
        Exercise exercise = findByIdOrThrow(id);
        List<MuscleGroup> groups = resolveMuscleGroups(request.getMuscleGroupIdsList());
        exerciseMapper.updateEntityFromRequest(request, exercise);
        replaceMuscleGroups(exercise, groups);
        return exerciseMapper.toResponse(exerciseRepository.save(exercise));
    }

    public void deleteExercise(Long id) {
        Exercise exercise = findByIdOrThrow(id);
        exerciseRepository.delete(exercise);
    }

    /**
     * Hibernate flushes new inserts before orphan deletes, so re-linking a group the exercise
     * already has would trip {@code uq_exercise_muscle_groups_exercise_group} (and moving the
     * primary flag the one-primary index). Flushing the removal first avoids both.
     */
    private void replaceMuscleGroups(Exercise exercise, List<MuscleGroup> groups) {
        exercise.getMuscleGroups().clear();
        exerciseRepository.flush();
        exercise.addMuscleGroups(groups);
    }

    private Exercise findByIdOrThrow(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise", id));
    }

    /**
     * De-duplicates {@code ids} keeping request order (the first becomes the primary group) and
     * loads them; the lowest unknown id is reported. Emptiness is checked by the controller.
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
