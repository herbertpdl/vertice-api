package com.vertice.api.plan.exercise;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import com.vertice.api.generated.grpc.exercise.v1.MuscleGroupResponse;
import com.vertice.api.grpc.CallerIdentity;
import com.vertice.api.grpc.CallerIdentityResolver;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    private static final MuscleGroup PEITO = muscleGroup(1L, "Peito");
    private static final MuscleGroup OMBROS = muscleGroup(3L, "Ombros");
    private static final MuscleGroup TRICEPS = muscleGroup(5L, "Tríceps");

    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private MuscleGroupRepository muscleGroupRepository;
    @Mock
    private CallerIdentityResolver callerIdentityResolver;
    @Mock
    private UserRepository userRepository;

    private ExerciseService service;

    @BeforeEach
    void setUp() {
        service = new ExerciseService(
                exerciseRepository, muscleGroupRepository, Mappers.getMapper(ExerciseMapper.class), callerIdentityResolver, userRepository);
    }

    @Test
    void createExercise_savesGroupsInRequestOrderWithoutPrimary() {
        stubAuthenticatedTrainer();
        stubGroups(PEITO, OMBROS, TRICEPS);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createExercise(request("Supino", 5L, 1L, 3L));

        Exercise saved = captureSaved();
        assertThat(saved.getMuscleGroups())
                .extracting(link -> link.getMuscleGroup().getId(), ExerciseMuscleGroup::isPrimary, ExerciseMuscleGroup::getCatalogOrder)
                .containsExactly(
                        tuple(5L, false, null),
                        tuple(1L, false, null),
                        tuple(3L, false, null));
        assertThat(saved.getOwner()).extracting(User::getId, User::getRole).containsExactly(10L, Role.TRAINER);
        assertThat(saved.getMuscleGroups()).allMatch(link -> link.getExercise() == saved);
        // No primary, so the response lists the groups by id.
        assertThat(response.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(1L, 3L, 5L);
        assertThat(response.getName()).isEqualTo("Supino");
    }

    @Test
    void createExercise_deduplicatesGroupIds() {
        stubAuthenticatedTrainer();
        stubGroups(PEITO, TRICEPS);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createExercise(request("Supino", 1L, 5L, 1L, 5L));

        assertThat(captureSaved().getMuscleGroups())
                .extracting(link -> link.getMuscleGroup().getId())
                .containsExactly(1L, 5L);
    }

    @Test
    void createExercise_unknownGroup_throwsInvalidArgumentNamingId() {
        stubAuthenticatedTrainer();
        stubGroups(PEITO);

        assertThatThrownBy(() -> service.createExercise(request("Supino", 1L, 99L, 42L)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessage("muscleGroupIds: unknown muscle group 42");
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void createExercise_whenAuthenticatedUserIsNotTrainer_throwsNotFound() {
        when(callerIdentityResolver.require()).thenReturn(new CallerIdentity(10L, Role.TRAINER));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, Role.CLIENT)));

        assertThatThrownBy(() -> service.createExercise(request("Supino", 1L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Trainer with id 10 not found");
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void updateExercise_replacesGroupsWithoutPrimary() {
        Exercise existing = exercise(1L, "Old name");
        existing.addMuscleGroups(List.of(PEITO, OMBROS));
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(existing));
        stubGroups(OMBROS, TRICEPS);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateExercise(1L, ExerciseRequest.newBuilder()
                .setName("New name").setDescription("New description").addMuscleGroupIds(5L).addMuscleGroupIds(3L).build());

        assertThat(existing.getMuscleGroups())
                .extracting(link -> link.getMuscleGroup().getId(), ExerciseMuscleGroup::isPrimary, ExerciseMuscleGroup::getCatalogOrder)
                .containsExactly(tuple(5L, false, null), tuple(3L, false, null));
        assertThat(response.getMuscleGroupsList()).extracting(MuscleGroupResponse::getId).containsExactly(3L, 5L);
        assertThat(response.getName()).isEqualTo("New name");
        assertThat(response.getDescription()).isEqualTo("New description");
    }

    @Test
    void getExercise_starterRow_isStarterTrueGroupsPrimaryFirst() {
        Exercise starter = exercise(1L, "Mergulho nas paralelas");
        addStarterLinks(starter, TRICEPS, 4, PEITO, OMBROS);
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(starter));

        var response = service.getExercise(1L);

        assertThat(response.getIsStarter()).isTrue();
        assertThat(response.getMuscleGroupsList())
                .extracting(MuscleGroupResponse::getId, MuscleGroupResponse::getName)
                .containsExactly(
                        tuple(5L, "Tríceps"),
                        tuple(1L, "Peito"),
                        tuple(3L, "Ombros"));
    }

    @Test
    void listMuscleGroups_returnsIdOrder() {
        when(muscleGroupRepository.findAllByOrderByIdAsc()).thenReturn(List.of(PEITO, OMBROS, TRICEPS));

        assertThat(service.listMuscleGroups())
                .extracting(MuscleGroupResponse::getId, MuscleGroupResponse::getName)
                .containsExactly(
                        tuple(1L, "Peito"),
                        tuple(3L, "Ombros"),
                        tuple(5L, "Tríceps"));
    }

    @Test
    void getExercise_withNullVideoUrl_returnsEmptyStringNotNull() {
        Exercise existing = exercise(1L, "Squat");
        existing.setVideoUrl(null);
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(service.getExercise(1L).getVideoUrl()).isEmpty();
    }

    @Test
    void getExercise_withNullDescription_returnsEmptyStringNotNull() {
        Exercise existing = exercise(1L, "Squat");
        existing.setDescription(null);
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(service.getExercise(1L).getDescription()).isEmpty();
    }

    @Test
    void updateExercise_throwsWhenMissing() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateExercise(99L, request("Name", 1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getExercise_throwsWhenMissing() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExercise(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteExercise_throwsWhenMissing() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteExercise(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @SuppressWarnings("unchecked")
    private void stubGroups(MuscleGroup... groups) {
        when(muscleGroupRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            Collection<Long> ids = (Collection<Long>) inv.getArgument(0, Iterable.class);
            return java.util.Arrays.stream(groups).filter(group -> ids.contains(group.getId())).toList();
        });
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

    private static Exercise exercise(Long id, String name) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setName(name);
        return exercise;
    }

    private static MuscleGroup muscleGroup(Long id, String name) {
        MuscleGroup group = new MuscleGroup();
        group.setId(id);
        group.setName(name);
        return group;
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private void stubAuthenticatedTrainer() {
        when(callerIdentityResolver.require()).thenReturn(new CallerIdentity(10L, Role.TRAINER));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, Role.TRAINER)));
    }
}
