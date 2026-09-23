package com.vertice.api.plan.exercise;

import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.grpc.CallerIdentityResolver;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

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
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @MockitoBean
    private CallerIdentityResolver callerIdentityResolver;

    private final List<Long> createdExerciseIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long unique = System.nanoTime();
        User trainer = user("trainer-%d@vertice.test".formatted(unique), "%011d".formatted(unique % 100_000_000_000L));
        trainer = userRepository.save(trainer);
        createdUserIds.add(trainer.getId());
        when(callerIdentityResolver.require()).thenReturn(new CallerIdentity(trainer.getId(), Role.TRAINER));
    }

    @AfterEach
    void tearDown() {
        exerciseRepository.deleteAllById(createdExerciseIds);
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void muscleGroups_seededInLaunchOrder() {
        List<MuscleGroup> groups = muscleGroupRepository.findAllByOrderByIdAsc();

        assertThat(groups).extracting(MuscleGroup::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L);
        assertThat(groups).extracting(MuscleGroup::getName).containsExactlyElementsOf(LAUNCH_GROUPS);
    }

    @Test
    void createExercise_groupsSentOutOfOrder_comeBackByIdWithNoPrimary() {
        ExerciseResponse created = exerciseService.createExercise(ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(5L).addMuscleGroupIds(2L).build());
        createdExerciseIds.add(created.getId());

        assertThat(created.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(2L, 5L);
        assertThat(exerciseService.getExercise(created.getId()).getMuscleGroupsList())
                .extracting(MuscleGroupResponse::getId, MuscleGroupResponse::getName)
                .containsExactly(tuple(2L, "Costas"), tuple(5L, "Tríceps"));
        assertThat(storedLinks(created.getId()))
                .containsExactly(tuple(2L, false, null), tuple(5L, false, null));
    }

    @Test
    void updateExercise_withOverlappingGroups_replacesLinksWithNoPrimary() {
        ExerciseResponse created = exerciseService.createExercise(ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(1L).addMuscleGroupIds(3L).build());
        createdExerciseIds.add(created.getId());

        // Several non-primary links on one exercise never collide with the one-primary partial index.
        ExerciseResponse updated = exerciseService.updateExercise(created.getId(), ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(3L).addMuscleGroupIds(1L).addMuscleGroupIds(5L).build());

        assertThat(updated.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(1L, 3L, 5L);
        assertThat(storedLinks(created.getId()))
                .containsExactly(tuple(1L, false, null), tuple(3L, false, null), tuple(5L, false, null));
    }

    @Test
    void onePrimaryIndex_stillRejectsASecondPrimary() {
        ExerciseResponse created = exerciseService.createExercise(ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(1L).addMuscleGroupIds(3L).build());
        createdExerciseIds.add(created.getId());
        jdbc.update("UPDATE exercise_muscle_groups SET is_primary = TRUE WHERE exercise_id = ? AND muscle_group_id = 1",
                created.getId());

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE exercise_muscle_groups SET is_primary = TRUE WHERE exercise_id = ? AND muscle_group_id = 3",
                created.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_exercise_muscle_groups_one_primary");
    }

    private List<org.assertj.core.groups.Tuple> storedLinks(Long exerciseId) {
        return jdbc.query("""
                SELECT muscle_group_id, is_primary, catalog_order
                FROM exercise_muscle_groups
                WHERE exercise_id = ?
                ORDER BY muscle_group_id
                """, (rs, i) -> tuple(rs.getLong(1), rs.getBoolean(2), rs.getObject(3)), exerciseId);
    }

    private static User user(String email, String cpf) {
        User user = new User();
        user.setName("Trainer");
        user.setEmail(email);
        user.setCpf(cpf);
        user.setPasswordHash("hash");
        user.setCref("12345");
        user.setRole(Role.TRAINER);
        return user;
    }
}
