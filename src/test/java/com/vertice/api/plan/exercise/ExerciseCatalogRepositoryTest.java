package com.vertice.api.plan.exercise;

import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Runs the exercise catalog's queries and migrations against real Postgres — ordering, seed
 * counts and cascade behavior that a mocked repository cannot prove (spec §0 D12). Seeds directly
 * and cleans up in {@code @AfterEach}, the shape {@code ReplaceWorkoutExercisesIntegrationTest}
 * set.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19105", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class ExerciseCatalogRepositoryTest {

    private static final List<String> LAUNCH_GROUPS = List.of(
            "Peito", "Costas", "Ombros", "Bíceps", "Tríceps", "Antebraço", "Quadríceps",
            "Posteriores de coxa", "Glúteos", "Panturrilhas", "Abdômen", "Lombar", "Trapézio", "Cardio");

    @Autowired
    private MuscleGroupRepository muscleGroupRepository;
    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private ExerciseService exerciseService;

    private final List<Long> createdExerciseIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        exerciseRepository.deleteAllById(createdExerciseIds);
    }

    @Test
    void muscleGroups_seededInLaunchOrder() {
        List<MuscleGroup> groups = muscleGroupRepository.findAllByOrderByIdAsc();

        assertThat(groups).extracting(MuscleGroup::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L);
        assertThat(groups).extracting(MuscleGroup::getName).containsExactlyElementsOf(LAUNCH_GROUPS);
    }

    @Test
    void updateExercise_withOverlappingGroups_replacesLinksAndMovesPrimary() {
        ExerciseResponse created = exerciseService.createExercise(ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(1L).addMuscleGroupIds(3L).build());
        createdExerciseIds.add(created.getId());

        ExerciseResponse updated = exerciseService.updateExercise(created.getId(), ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(3L).addMuscleGroupIds(1L).addMuscleGroupIds(5L).build());

        assertThat(updated.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(3L, 1L, 5L);
        assertThat(exerciseService.getExercise(created.getId()).getMuscleGroupsList())
                .extracting(MuscleGroupResponse::getId, MuscleGroupResponse::getName)
                .containsExactly(tuple(3L, "Ombros"), tuple(1L, "Peito"), tuple(5L, "Tríceps"));
    }
}
