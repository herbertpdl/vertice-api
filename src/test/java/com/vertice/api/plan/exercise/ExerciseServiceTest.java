package com.vertice.api.plan.exercise;

import com.vertice.api.common.exception.ExerciseInUseException;
import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.plan.workout.WorkoutExerciseRepository;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    private static final MuscleGroup PEITO = muscleGroup(1L, "Peito");
    private static final MuscleGroup OMBROS = muscleGroup(3L, "Ombros");
    private static final MuscleGroup TRICEPS = muscleGroup(5L, "Tríceps");

    private static final CallerIdentity TRAINER = new CallerIdentity(10L, Role.TRAINER);
    private static final CallerIdentity OTHER_TRAINER = new CallerIdentity(11L, Role.TRAINER);
    private static final CallerIdentity ADMIN = new CallerIdentity(1L, Role.ADMIN);
    private static final CallerIdentity CLIENT = new CallerIdentity(20L, Role.CLIENT);

    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private MuscleGroupRepository muscleGroupRepository;
    @Mock
    private WorkoutExerciseRepository workoutExerciseRepository;
    @Mock
    private UserRepository userRepository;

    private ExerciseService service;

    @BeforeEach
    void setUp() {
        service = new ExerciseService(exerciseRepository, muscleGroupRepository, workoutExerciseRepository,
                userRepository, Mappers.getMapper(ExerciseMapper.class));
    }

    // --- Muscle groups ---

    @Test
    void createExercise_savesGroupsInRequestOrderWithoutPrimary() {
        stubOwner(TRAINER);
        stubGroups(PEITO, OMBROS, TRICEPS);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createExercise(TRAINER, request("Supino", 5L, 1L, 3L));

        Exercise saved = captureSaved();
        assertThat(saved.getMuscleGroups())
                .extracting(link -> link.getMuscleGroup().getId(), ExerciseMuscleGroup::isPrimary, ExerciseMuscleGroup::getCatalogOrder)
                .containsExactly(tuple(5L, false, null), tuple(1L, false, null), tuple(3L, false, null));
        assertThat(saved.getMuscleGroups()).allMatch(link -> link.getExercise() == saved);
        // No primary, so the response lists the groups by id.
        assertThat(response.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(1L, 3L, 5L);
        assertThat(response.getName()).isEqualTo("Supino");
    }

    @Test
    void createExercise_deduplicatesGroupIds() {
        stubOwner(TRAINER);
        stubGroups(PEITO, TRICEPS);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createExercise(TRAINER, request("Supino", 1L, 5L, 1L, 5L));

        assertThat(captureSaved().getMuscleGroups())
                .extracting(link -> link.getMuscleGroup().getId())
                .containsExactly(1L, 5L);
    }

    @Test
    void createExercise_unknownGroup_throwsInvalidArgumentNamingId() {
        stubGroups(PEITO);

        assertThatThrownBy(() -> service.createExercise(TRAINER, request("Supino", 1L, 99L, 42L)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessage("muscleGroupIds: unknown muscle group 42");
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void updateExercise_replacesGroupsWithoutPrimary() {
        Exercise existing = privateExercise(1L, "Old name", TRAINER);
        existing.addMuscleGroups(List.of(PEITO, OMBROS));
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubGroups(OMBROS, TRICEPS);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateExercise(TRAINER, 1L, request("New name", 5L, 3L));

        assertThat(existing.getMuscleGroups())
                .extracting(link -> link.getMuscleGroup().getId(), ExerciseMuscleGroup::isPrimary, ExerciseMuscleGroup::getCatalogOrder)
                .containsExactly(tuple(5L, false, null), tuple(3L, false, null));
        assertThat(response.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(3L, 5L);
    }

    @Test
    void getExercise_starterRow_isStarterTrueGroupsPrimaryFirst() {
        Exercise starter = starterExercise(1L, "Mergulho nas paralelas");
        addStarterLinks(starter, TRICEPS, 4, PEITO, OMBROS);
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(starter));

        var response = service.getExercise(TRAINER, 1L);

        assertThat(response.getIsStarter()).isTrue();
        assertThat(response.getMuscleGroupsList())
                .extracting(MuscleGroupResponse::getId, MuscleGroupResponse::getName)
                .containsExactly(tuple(5L, "Tríceps"), tuple(1L, "Peito"), tuple(3L, "Ombros"));
    }

    @Test
    void listMuscleGroups_returnsIdOrder() {
        when(muscleGroupRepository.findAllByOrderByIdAsc()).thenReturn(List.of(PEITO, OMBROS, TRICEPS));

        assertThat(service.listMuscleGroups())
                .extracting(MuscleGroupResponse::getId, MuscleGroupResponse::getName)
                .containsExactly(tuple(1L, "Peito"), tuple(3L, "Ombros"), tuple(5L, "Tríceps"));
    }

    @Test
    void getExercise_withNullVideoUrl_returnsEmptyStringNotNull() {
        Exercise existing = starterExercise(1L, "Squat");
        existing.setVideoUrl(null);
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(service.getExercise(TRAINER, 1L).getVideoUrl()).isEmpty();
    }

    @Test
    void getExercise_withNullDescription_returnsEmptyStringNotNull() {
        Exercise existing = starterExercise(1L, "Squat");
        existing.setDescription(null);
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(service.getExercise(TRAINER, 1L).getDescription()).isEmpty();
    }

    // --- ListExercises ---

    @Test
    void listExercises_clientRefused() {
        assertThatThrownBy(() -> service.listExercises(CLIENT))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("Role CLIENT is not allowed to list exercises");
        verifyNoInteractions(exerciseRepository);
    }

    @Test
    void listExercises_trainerPassesOwnId_adminPassesOwnId() {
        when(exerciseRepository.findByOwnerIdIsNullOrOwnerId(any())).thenReturn(List.of());

        service.listExercises(TRAINER);
        service.listExercises(ADMIN);

        verify(exerciseRepository).findByOwnerIdIsNullOrOwnerId(TRAINER.userId());
        verify(exerciseRepository).findByOwnerIdIsNullOrOwnerId(ADMIN.userId());
    }

    // --- GetExercise ---

    @Test
    void getExercise_missing_notFoundBeforeVisibility() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExercise(CLIENT, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Exercise with id 99 not found");
        verifyNoInteractions(workoutExerciseRepository);
    }

    @Test
    void getExercise_trainerOtherTrainers_permissionDenied() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(privateExercise(1L, "Mine", OTHER_TRAINER)));

        assertThatThrownBy(() -> service.getExercise(TRAINER, 1L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("You do not have access to exercise 1");
    }

    @Test
    void getExercise_trainerOwn_allowed() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(privateExercise(1L, "Mine", TRAINER)));

        var response = service.getExercise(TRAINER, 1L);

        assertThat(response.getIsStarter()).isFalse();
    }

    @Test
    void getExercise_adminPrivate_permissionDenied() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(privateExercise(1L, "Mine", TRAINER)));

        assertThatThrownBy(() -> service.getExercise(ADMIN, 1L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("You do not have access to exercise 1");
    }

    @Test
    void getExercise_adminStarter_allowed() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(starterExercise(1L, "Agachamento")));

        assertThat(service.getExercise(ADMIN, 1L).getName()).isEqualTo("Agachamento");
    }

    @Test
    void getExercise_clientInOwnWorkout_allowed() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(privateExercise(1L, "Mine", TRAINER)));
        when(workoutExerciseRepository.existsByExerciseIdAndWorkout_TrainingPlan_Client_Id(1L, CLIENT.userId()))
                .thenReturn(true);

        assertThat(service.getExercise(CLIENT, 1L).getName()).isEqualTo("Mine");
    }

    @Test
    void getExercise_clientOutsideOwnWorkouts_permissionDenied() {
        // Even a starter row is refused to a client when no plan of theirs uses it (R22).
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(starterExercise(1L, "Agachamento")));
        when(workoutExerciseRepository.existsByExerciseIdAndWorkout_TrainingPlan_Client_Id(1L, CLIENT.userId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getExercise(CLIENT, 1L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("You do not have access to exercise 1");
    }

    // --- CreateExercise ---

    @Test
    void createExercise_clientRefused() {
        assertThatThrownBy(() -> service.createExercise(CLIENT, request("Supino", 1L)))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("Role CLIENT is not allowed to create exercises");
        verifyNoInteractions(exerciseRepository, muscleGroupRepository);
    }

    @Test
    void createExercise_adminRefused() {
        assertThatThrownBy(() -> service.createExercise(ADMIN, request("Supino", 1L)))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("Role ADMIN is not allowed to create exercises");
        verifyNoInteractions(exerciseRepository, muscleGroupRepository);
    }

    @Test
    void createExercise_setsOwnerToCaller() {
        stubOwner(TRAINER);
        stubGroups(PEITO);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createExercise(TRAINER, request("Supino", 1L));

        assertThat(captureSaved().getOwner().getId()).isEqualTo(TRAINER.userId());
        assertThat(response.getIsStarter()).isFalse();
    }

    // --- UpdateExercise ---

    @Test
    void updateExercise_starter_permissionDeniedStarterMessage() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(starterExercise(1L, "Agachamento")));

        assertThatThrownBy(() -> service.updateExercise(TRAINER, 1L, request("Renamed", 1L)))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("Exercise 1 belongs to the shared starter set and cannot be changed");
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void updateExercise_otherTrainers_permissionDenied() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(privateExercise(1L, "Theirs", OTHER_TRAINER)));

        assertThatThrownBy(() -> service.updateExercise(TRAINER, 1L, request("Renamed", 1L)))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("You do not have access to exercise 1");
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void updateExercise_own_replacesAllFields() {
        Exercise existing = privateExercise(1L, "Old name", TRAINER);
        existing.setDescription("Old description");
        existing.setVideoUrl("https://old.example.com");
        existing.addMuscleGroups(List.of(PEITO));
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubGroups(OMBROS, TRICEPS);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateExercise(TRAINER, 1L, ExerciseRequest.newBuilder()
                .setName("New name").setDescription("New description").setVideoUrl("https://new.example.com")
                .addMuscleGroupIds(5L).addMuscleGroupIds(3L).build());

        assertThat(response.getName()).isEqualTo("New name");
        assertThat(response.getDescription()).isEqualTo("New description");
        assertThat(response.getVideoUrl()).isEqualTo("https://new.example.com");
        assertThat(response.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(3L, 5L);
        assertThat(response.getIsStarter()).isFalse();
    }

    @Test
    void updateExercise_clientRefused() {
        assertThatThrownBy(() -> service.updateExercise(CLIENT, 1L, request("Renamed", 1L)))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("Role CLIENT is not allowed to change exercises");
        verifyNoInteractions(exerciseRepository);
    }

    @Test
    void updateExercise_throwsWhenMissing() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateExercise(TRAINER, 99L, request("Name", 1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- DeleteExercise ---

    @Test
    void deleteExercise_starter_permissionDeniedStarterMessage() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(starterExercise(1L, "Agachamento")));

        assertThatThrownBy(() -> service.deleteExercise(TRAINER, 1L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("Exercise 1 belongs to the shared starter set and cannot be deleted");
        verify(exerciseRepository, never()).delete(any());
    }

    @Test
    void deleteExercise_otherTrainers_permissionDenied() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(privateExercise(1L, "Theirs", OTHER_TRAINER)));

        assertThatThrownBy(() -> service.deleteExercise(TRAINER, 1L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("You do not have access to exercise 1");
        verify(exerciseRepository, never()).delete(any());
    }

    @Test
    void deleteExercise_clientRefused() {
        assertThatThrownBy(() -> service.deleteExercise(CLIENT, 1L))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessage("Role CLIENT is not allowed to delete exercises");
        verifyNoInteractions(exerciseRepository);
    }

    @Test
    void deleteExercise_inUse_throwsExerciseInUse() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(privateExercise(1L, "Mine", TRAINER)));
        when(workoutExerciseRepository.existsByExerciseId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteExercise(TRAINER, 1L))
                .isInstanceOf(ExerciseInUseException.class)
                .hasMessage("Exercise 1 is used by a workout and cannot be deleted");
        verify(exerciseRepository, never()).delete(any());
    }

    @Test
    void deleteExercise_unused_deletes() {
        Exercise own = privateExercise(1L, "Mine", TRAINER);
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(own));
        when(workoutExerciseRepository.existsByExerciseId(1L)).thenReturn(false);

        service.deleteExercise(TRAINER, 1L);

        verify(exerciseRepository).delete(own);
    }

    @Test
    void deleteExercise_checksStarterAndOwnershipBeforeInUse() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(starterExercise(1L, "Agachamento")));
        when(exerciseRepository.findById(2L)).thenReturn(Optional.of(privateExercise(2L, "Theirs", OTHER_TRAINER)));

        assertThatThrownBy(() -> service.deleteExercise(TRAINER, 1L)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.deleteExercise(TRAINER, 2L)).isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(workoutExerciseRepository);
    }

    @Test
    void deleteExercise_throwsWhenMissing() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteExercise(TRAINER, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getExercise_throwsWhenMissing() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExercise(TRAINER, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private void stubGroups(MuscleGroup... groups) {
        when(muscleGroupRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            Collection<Long> ids = (Collection<Long>) inv.getArgument(0, Iterable.class);
            return Arrays.stream(groups).filter(group -> ids.contains(group.getId())).toList();
        });
    }

    private void stubOwner(CallerIdentity caller) {
        when(userRepository.getReferenceById(caller.userId())).thenReturn(user(caller.userId()));
    }

    private Exercise captureSaved() {
        ArgumentCaptor<Exercise> captor = ArgumentCaptor.forClass(Exercise.class);
        verify(exerciseRepository).save(captor.capture());
        return captor.getValue();
    }

    /** Links shaped like V24's: one primary with a catalog order, the rest secondary. */
    private static void addStarterLinks(Exercise exercise, MuscleGroup primary, int catalogOrder, MuscleGroup... secondary) {
        exercise.getMuscleGroups().add(link(exercise, primary, true, catalogOrder));
        for (MuscleGroup group : secondary) {
            exercise.getMuscleGroups().add(link(exercise, group, false, null));
        }
    }

    private static ExerciseMuscleGroup link(Exercise exercise, MuscleGroup group, boolean primary, Integer catalogOrder) {
        ExerciseMuscleGroup link = new ExerciseMuscleGroup();
        link.setExercise(exercise);
        link.setMuscleGroup(group);
        link.setPrimary(primary);
        link.setCatalogOrder(catalogOrder);
        return link;
    }

    private static ExerciseRequest request(String name, Long... groupIds) {
        return ExerciseRequest.newBuilder().setName(name).addAllMuscleGroupIds(List.of(groupIds)).build();
    }

    private static Exercise starterExercise(Long id, String name) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setName(name);
        return exercise;
    }

    private static Exercise privateExercise(Long id, String name, CallerIdentity owner) {
        Exercise exercise = starterExercise(id, name);
        exercise.setOwner(user(owner.userId()));
        return exercise;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(Role.TRAINER);
        return user;
    }

    private static MuscleGroup muscleGroup(Long id, String name) {
        MuscleGroup group = new MuscleGroup();
        group.setId(id);
        group.setName(name);
        return group;
    }
}
