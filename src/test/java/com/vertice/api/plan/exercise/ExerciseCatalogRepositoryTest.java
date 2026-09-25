package com.vertice.api.plan.exercise;

import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseResponse;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;

    private final List<Long> createdExerciseIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        exerciseRepository.deleteAllById(createdExerciseIds);
        userRepository.deleteAllById(createdUserIds);
    }

    private CallerIdentity newTrainer(String name) {
        return new CallerIdentity(newUser(name, Role.TRAINER), Role.TRAINER);
    }

    private Long newUser(String name, Role role) {
        String unique = String.valueOf(System.nanoTime());
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "-" + unique + "@example.com");
        user.setCpf(unique.substring(unique.length() - 11));
        user.setPasswordHash("hash");
        user.setRole(role);
        user = userRepository.save(user);
        createdUserIds.add(user.getId());
        return user.getId();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void muscleGroups_seededInLaunchOrder() {
        List<MuscleGroup> groups = muscleGroupRepository.findAllByOrderByIdAsc();

        assertThat(groups).extracting(MuscleGroup::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L);
        assertThat(groups).extracting(MuscleGroup::getName).containsExactlyElementsOf(LAUNCH_GROUPS);
    }

    @Test
    void starterSet_has199ExercisesWithNullOwner() {
        assertThat(count("SELECT count(*) FROM exercises WHERE owner_id IS NULL")).isEqualTo(199);
    }

    @Test
    void starterSet_has283LinksAcross14Groups() {
        assertThat(count("""
                SELECT count(*) FROM exercise_muscle_groups emg
                JOIN exercises e ON e.id = emg.exercise_id
                WHERE e.owner_id IS NULL
                """)).isEqualTo(283);
        assertThat(count("""
                SELECT count(DISTINCT emg.muscle_group_id) FROM exercise_muscle_groups emg
                JOIN exercises e ON e.id = emg.exercise_id
                WHERE e.owner_id IS NULL
                """)).isEqualTo(14);
        assertThat(count("""
                SELECT count(*) FROM exercise_muscle_groups emg
                JOIN exercises e ON e.id = emg.exercise_id
                WHERE e.owner_id IS NULL AND NOT emg.is_primary AND emg.catalog_order IS NULL
                """)).isEqualTo(84);
    }

    @Test
    void starterSet_everyExerciseHasExactlyOnePrimaryWithCatalogOrder() {
        assertThat(count("""
                SELECT count(*) FROM exercises e
                WHERE e.owner_id IS NULL
                  AND (SELECT count(*) FROM exercise_muscle_groups emg
                       WHERE emg.exercise_id = e.id AND emg.is_primary AND emg.catalog_order IS NOT NULL) <> 1
                """)).isZero();
    }

    @Test
    void starterSet_noDescriptionOrVideo() {
        assertThat(count("""
                SELECT count(*) FROM exercises
                WHERE owner_id IS NULL AND (description IS NOT NULL OR video_url IS NOT NULL)
                """)).isZero();
    }

    @Test
    void starterSet_perGroupPrimaryCountsMatchPrd() {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("Peito", 20), Map.entry("Costas", 22), Map.entry("Ombros", 20), Map.entry("Bíceps", 16),
                Map.entry("Tríceps", 16), Map.entry("Antebraço", 9), Map.entry("Quadríceps", 20),
                Map.entry("Posteriores de coxa", 13), Map.entry("Glúteos", 14), Map.entry("Panturrilhas", 8),
                Map.entry("Abdômen", 17), Map.entry("Lombar", 7), Map.entry("Trapézio", 7), Map.entry("Cardio", 10));

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT mg.name, count(*) AS primaries, max(emg.catalog_order) AS last_order
                FROM exercise_muscle_groups emg
                JOIN exercises e ON e.id = emg.exercise_id
                JOIN muscle_groups mg ON mg.id = emg.muscle_group_id
                WHERE e.owner_id IS NULL AND emg.is_primary
                GROUP BY mg.name
                """);

        assertThat(rows).hasSize(14).allSatisfy(row -> {
            int primaries = ((Number) row.get("primaries")).intValue();
            assertThat(primaries).isEqualTo(expected.get((String) row.get("name")));
            // catalog_order runs 1..n within each group.
            assertThat(((Number) row.get("last_order")).intValue()).isEqualTo(primaries);
        });
    }

    @Test
    void starterSet_namesUnique() {
        assertThat(count("SELECT count(DISTINCT name) FROM exercises WHERE owner_id IS NULL")).isEqualTo(199);
    }

    // --- findVisible (R45-R50) ---

    private static final long PEITO = 1L;
    private static final long LOMBAR = 12L;

    @Test
    void findVisible_ownFirstThenStarterByName() {
        CallerIdentity trainer = newTrainer("Trainer");
        createOwn(trainer, "Zeta own", PEITO);
        createOwn(trainer, "Alpha own", LOMBAR);

        List<String> names = names(exerciseRepository.findVisible(trainer.userId(), 0, ""));

        assertThat(names).hasSize(201);
        assertThat(names.subList(0, 2)).containsExactly("Alpha own", "Zeta own");
        assertThat(names.subList(2, 201)).containsExactlyElementsOf(starterNamesByName());
    }

    @Test
    void findVisible_groupFilter_primaryInCatalogOrderThenSecondaryByName() {
        CallerIdentity trainer = newTrainer("Trainer");
        createOwn(trainer, "Own chest press", PEITO);

        List<String> names = names(exerciseRepository.findVisible(trainer.userId(), PEITO, ""));

        assertThat(names).hasSize(25);
        assertThat(names.getFirst()).isEqualTo("Own chest press");
        List<String> starter = names.subList(1, 25);
        assertThat(starter.get(0)).isEqualTo("Supino reto com barra");
        assertThat(starter.get(1)).isEqualTo("Supino inclinado com halteres");
        assertThat(starter.subList(0, 20)).containsExactlyElementsOf(jdbc.queryForList("""
                SELECT e.name FROM exercises e
                JOIN exercise_muscle_groups g ON g.exercise_id = e.id
                WHERE e.owner_id IS NULL AND g.muscle_group_id = ? AND g.is_primary
                ORDER BY g.catalog_order
                """, String.class, PEITO));
        List<String> secondary = starter.subList(20, 24);
        assertThat(secondary).contains("Mergulho nas paralelas", "Supino fechado com barra");
        assertThat(secondary).containsExactlyElementsOf(jdbc.queryForList("""
                SELECT e.name FROM exercises e
                JOIN exercise_muscle_groups g ON g.exercise_id = e.id
                WHERE e.owner_id IS NULL AND g.muscle_group_id = ? AND NOT g.is_primary
                ORDER BY e.name
                """, String.class, PEITO));
    }

    @Test
    void findVisible_groupFilter_includesSecondaryGroupExercises() {
        CallerIdentity trainer = newTrainer("Trainer");

        List<String> names = names(exerciseRepository.findVisible(trainer.userId(), LOMBAR, ""));

        assertThat(names).hasSize(19).contains("Levantamento terra com barra");
        assertThat(names.indexOf("Levantamento terra com barra")).isGreaterThanOrEqualTo(7);
    }

    @Test
    void findVisible_search_caseInsensitiveSubstring() {
        CallerIdentity trainer = newTrainer("Trainer");

        List<String> names = names(exerciseRepository.findVisible(trainer.userId(), 0, "SUPINO"));

        assertThat(names).isNotEmpty()
                .contains("Supino reto com barra")
                .allMatch(name -> name.toLowerCase().contains("supino"));
        assertThat(names).hasSize(count("SELECT count(*) FROM exercises WHERE owner_id IS NULL AND name ILIKE '%supino%'"));
    }

    @Test
    void findVisible_search_wildcardsAreLiteral() {
        CallerIdentity trainer = newTrainer("Trainer");

        assertThat(exerciseService.listExercises(trainer, 0, "%")).isEmpty();
        assertThat(exerciseService.listExercises(trainer, 0, "_")).isEmpty();
        assertThat(exerciseService.listExercises(trainer, 0, "\\")).isEmpty();
    }

    @Test
    void findVisible_search_ignoresOtherTrainersRows() {
        CallerIdentity trainer = newTrainer("Trainer");
        CallerIdentity other = newTrainer("Other");
        createOwn(other, "Supino secreto", PEITO);

        assertThat(names(exerciseRepository.findVisible(trainer.userId(), 0, "secreto"))).isEmpty();
        assertThat(names(exerciseRepository.findVisible(trainer.userId(), PEITO, ""))).doesNotContain("Supino secreto");
        assertThat(names(exerciseRepository.findVisible(other.userId(), 0, "secreto"))).containsExactly("Supino secreto");
    }

    @Test
    void findVisible_adminId_returnsStarterOnly() {
        CallerIdentity trainer = newTrainer("Trainer");
        createOwn(trainer, "Trainer private", PEITO);
        CallerIdentity admin = new CallerIdentity(newUser("Admin", Role.ADMIN), Role.ADMIN);

        List<Exercise> visible = exerciseRepository.findVisible(admin.userId(), 0, "");

        assertThat(visible).hasSize(199).allMatch(Exercise::isStarter);
    }

    @Test
    void findVisible_noFilter_starterByName() {
        CallerIdentity trainer = newTrainer("Trainer");

        assertThat(names(exerciseRepository.findVisible(trainer.userId(), 0, "")))
                .containsExactlyElementsOf(starterNamesByName());
    }

    @Test
    void findVisible_groupAndSearch_combined() {
        CallerIdentity trainer = newTrainer("Trainer");
        createOwn(trainer, "Supino inclinado caseiro", PEITO);
        createOwn(trainer, "Remada inclinado caseira", LOMBAR);

        List<String> names = names(exerciseRepository.findVisible(trainer.userId(), PEITO, "inclinado"));

        assertThat(names).containsExactly(
                "Supino inclinado caseiro",
                "Supino inclinado com halteres",
                "Supino inclinado com barra",
                "Crucifixo inclinado com halteres",
                "Supino inclinado na máquina",
                "Mergulho nas paralelas com tronco inclinado",
                "Supino inclinado no Smith");
    }

    private void createOwn(CallerIdentity trainer, String name, long groupId) {
        createdExerciseIds.add(exerciseService.createExercise(trainer, ExerciseRequest.newBuilder()
                .setName(name).addMuscleGroupIds(groupId).build()).getId());
    }

    private List<String> starterNamesByName() {
        return jdbc.queryForList("SELECT name FROM exercises WHERE owner_id IS NULL ORDER BY name", String.class);
    }

    private static List<String> names(List<Exercise> exercises) {
        return exercises.stream().map(Exercise::getName).toList();
    }

    @Test
    void createExercise_groupsSentOutOfOrder_comeBackByIdWithNoPrimary() {
        CallerIdentity trainer = newTrainer("Trainer");
        ExerciseResponse created = exerciseService.createExercise(trainer, ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(5L).addMuscleGroupIds(2L).build());
        createdExerciseIds.add(created.getId());

        assertThat(created.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(2L, 5L);
        assertThat(exerciseService.getExercise(trainer, created.getId()).getMuscleGroupsList())
                .extracting(MuscleGroupResponse::getId, MuscleGroupResponse::getName)
                .containsExactly(tuple(2L, "Costas"), tuple(5L, "Tríceps"));
        assertThat(storedLinks(created.getId()))
                .containsExactly(tuple(2L, false, null), tuple(5L, false, null));
    }

    @Test
    void updateExercise_withOverlappingGroups_replacesLinksWithNoPrimary() {
        CallerIdentity trainer = newTrainer("Trainer");
        ExerciseResponse created = exerciseService.createExercise(trainer, ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(1L).addMuscleGroupIds(3L).build());
        createdExerciseIds.add(created.getId());

        // Several non-primary links on one exercise never collide with the one-primary partial index.
        ExerciseResponse updated = exerciseService.updateExercise(trainer, created.getId(), ExerciseRequest.newBuilder()
                .setName("Test exercise").addMuscleGroupIds(3L).addMuscleGroupIds(1L).addMuscleGroupIds(5L).build());

        assertThat(updated.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(1L, 3L, 5L);
        assertThat(storedLinks(created.getId()))
                .containsExactly(tuple(1L, false, null), tuple(3L, false, null), tuple(5L, false, null));
    }

    @Test
    void onePrimaryIndex_stillRejectsASecondPrimary() {
        ExerciseResponse created = exerciseService.createExercise(newTrainer("Trainer"), ExerciseRequest.newBuilder()
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
}
