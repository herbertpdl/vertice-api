package com.vertice.api.plan;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.PlanLevel;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {

    @Mock
    private TrainingPlanRepository trainingPlanRepository;

    @Mock
    private UserRepository userRepository;

    private TrainingPlanService service;

    @BeforeEach
    void setUp() {
        service = new TrainingPlanService(trainingPlanRepository, Mappers.getMapper(TrainingPlanMapper.class), userRepository);
    }

    private static User user(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private static TrainingPlan plan(long id, User trainer, User client) {
        TrainingPlan plan = new TrainingPlan();
        plan.setId(id);
        plan.setName("Plan");
        plan.setTrainer(trainer);
        plan.setClient(client);
        plan.setStartDate(java.time.LocalDate.parse("2026-01-05"));
        plan.setEndDate(java.time.LocalDate.parse("2026-03-30"));
        plan.setLevel(com.vertice.api.plan.PlanLevel.INTERMEDIATE);
        return plan;
    }

    private static TrainingPlanCreateRequest.Builder validCreateRequest() {
        return TrainingPlanCreateRequest.newBuilder()
                .setName("12-Week Strength Program")
                .setDescription("Progressive overload")
                .setTrainerId(1L)
                .setClientId(2L)
                .setStartDate("2026-01-05")
                .setEndDate("2026-03-30")
                .setLevel(PlanLevel.INTERMEDIATE);
    }

    @Test
    void createTrainingPlan_setsTrainerAndClientAndSaves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.TRAINER)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.CLIENT)));
        when(trainingPlanRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createTrainingPlan(validCreateRequest().build());

        assertThat(response.getName()).isEqualTo("12-Week Strength Program");
        assertThat(response.getTrainerId()).isEqualTo(1L);
        assertThat(response.getClientId()).isEqualTo(2L);
        assertThat(response.getStartDate()).isEqualTo("2026-01-05");
        assertThat(response.getEndDate()).isEqualTo("2026-03-30");
        assertThat(response.getLevel()).isEqualTo(PlanLevel.INTERMEDIATE);
    }

    @Test
    void createTrainingPlan_throwsWhenTrainerMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        var request = validCreateRequest().setTrainerId(99L).build();

        assertThatThrownBy(() -> service.createTrainingPlan(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void createTrainingPlan_throwsWhenUserIsNotTrainer() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.CLIENT)));

        var request = validCreateRequest().build();

        assertThatThrownBy(() -> service.createTrainingPlan(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void createTrainingPlan_throwsWhenClientMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.TRAINER)));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        var request = validCreateRequest().build();

        assertThatThrownBy(() -> service.createTrainingPlan(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void createTrainingPlan_throwsWhenUserIsNotClient() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.TRAINER)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.TRAINER)));

        var request = validCreateRequest().build();

        assertThatThrownBy(() -> service.createTrainingPlan(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void createTrainingPlan_throwsWhenEndDateBeforeStartDate() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.TRAINER)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.CLIENT)));

        var request = validCreateRequest().setStartDate("2026-03-30").setEndDate("2026-01-05").build();

        assertThatThrownBy(() -> service.createTrainingPlan(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void createTrainingPlan_allowsEndDateEqualToStartDate() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.TRAINER)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.CLIENT)));
        when(trainingPlanRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = validCreateRequest().setStartDate("2026-01-05").setEndDate("2026-01-05").build();

        var response = service.createTrainingPlan(request);

        assertThat(response.getStartDate()).isEqualTo(response.getEndDate());
    }

    @Test
    void createTrainingPlan_throwsWhenDateUnparsable() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.TRAINER)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, Role.CLIENT)));

        var request = validCreateRequest().setStartDate("not-a-date").build();

        assertThatThrownBy(() -> service.createTrainingPlan(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void updateTrainingPlan_updatesFieldsAndReassignsClient() {
        User trainer = user(1L, Role.TRAINER);
        User newClient = user(3L, Role.CLIENT);
        TrainingPlan existing = new TrainingPlan();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setTrainer(trainer);
        existing.setClient(user(2L, Role.CLIENT));
        existing.setLevel(com.vertice.api.plan.PlanLevel.BEGINNER);

        when(trainingPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(3L)).thenReturn(Optional.of(newClient));
        when(trainingPlanRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlanRequest request = TrainingPlanRequest.newBuilder()
                .setName("New Name")
                .setDescription("New description")
                .setClientId(3L)
                .setStartDate("2026-02-01")
                .setEndDate("2026-04-01")
                .setLevel(PlanLevel.ADVANCED)
                .build();

        var response = service.updateTrainingPlan(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getTrainerId()).isEqualTo(1L);
        assertThat(response.getClientId()).isEqualTo(3L);
        assertThat(response.getLevel()).isEqualTo(PlanLevel.ADVANCED);
    }

    @Test
    void updateTrainingPlan_throwsWhenMissing() {
        when(trainingPlanRepository.findById(99L)).thenReturn(Optional.empty());

        TrainingPlanRequest request = TrainingPlanRequest.newBuilder()
                .setName("Name").setClientId(2L).setStartDate("2026-01-05").setEndDate("2026-03-30")
                .setLevel(PlanLevel.BEGINNER).build();

        assertThatThrownBy(() -> service.updateTrainingPlan(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTrainingPlan_throwsWhenNewClientMissing() {
        TrainingPlan existing = new TrainingPlan();
        existing.setId(1L);
        existing.setTrainer(user(1L, Role.TRAINER));
        existing.setClient(user(2L, Role.CLIENT));
        when(trainingPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        TrainingPlanRequest request = TrainingPlanRequest.newBuilder()
                .setName("Name").setClientId(99L).setStartDate("2026-01-05").setEndDate("2026-03-30")
                .setLevel(PlanLevel.BEGINNER).build();

        assertThatThrownBy(() -> service.updateTrainingPlan(1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void listTrainingPlans_filtersByTrainerOnly() {
        TrainingPlan plan = plan(1L, user(1L, Role.TRAINER), user(2L, Role.CLIENT));

        when(trainingPlanRepository.findByTrainerId(1L)).thenReturn(List.of(plan));

        var responses = service.listTrainingPlans(1L, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getTrainerId()).isEqualTo(1L);
        assertThat(responses.getFirst().getDescription()).isEmpty();
    }

    @Test
    void listTrainingPlans_filtersByClientOnly() {
        TrainingPlan plan = plan(1L, user(1L, Role.TRAINER), user(2L, Role.CLIENT));

        when(trainingPlanRepository.findByClientId(2L)).thenReturn(List.of(plan));

        var responses = service.listTrainingPlans(null, 2L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getClientId()).isEqualTo(2L);
    }

    @Test
    void listTrainingPlans_filtersByBothTrainerAndClient() {
        TrainingPlan matching = plan(1L, user(1L, Role.TRAINER), user(2L, Role.CLIENT));
        TrainingPlan otherClient = plan(2L, user(1L, Role.TRAINER), user(3L, Role.CLIENT));

        when(trainingPlanRepository.findByTrainerId(1L)).thenReturn(List.of(matching, otherClient));

        var responses = service.listTrainingPlans(1L, 2L);

        assertThat(responses).extracting(r -> r.getId()).containsExactly(1L);
    }

    @Test
    void listTrainingPlans_returnsAllWhenNeitherFilterSet() {
        TrainingPlan plan = plan(1L, user(1L, Role.TRAINER), user(2L, Role.CLIENT));

        when(trainingPlanRepository.findAll()).thenReturn(List.of(plan));

        var responses = service.listTrainingPlans(null, null);

        assertThat(responses).hasSize(1);
    }

    @Test
    void getTrainingPlan_throwsWhenMissing() {
        when(trainingPlanRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTrainingPlan(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTrainingPlan_throwsWhenMissing() {
        when(trainingPlanRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTrainingPlan(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
