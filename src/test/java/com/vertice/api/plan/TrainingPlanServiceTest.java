package com.vertice.api.plan;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
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

    @Test
    void createTrainingPlan_setsTrainerAndSaves() {
        User trainer = new User();
        trainer.setId(1L);
        trainer.setRole(Role.TRAINER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(trainer));
        when(trainingPlanRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlanCreateRequest request = TrainingPlanCreateRequest.newBuilder()
                .setName("12-Week Strength Program")
                .setDescription("Progressive overload")
                .setTrainerId(1L)
                .build();

        var response = service.createTrainingPlan(request);

        assertThat(response.getName()).isEqualTo("12-Week Strength Program");
        assertThat(response.getTrainerId()).isEqualTo(1L);
    }

    @Test
    void createTrainingPlan_throwsWhenTrainerMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        TrainingPlanCreateRequest request = TrainingPlanCreateRequest.newBuilder()
                .setName("Plan")
                .setTrainerId(99L)
                .build();

        assertThatThrownBy(() -> service.createTrainingPlan(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void createTrainingPlan_throwsWhenUserIsNotTrainer() {
        User client = new User();
        client.setId(1L);
        client.setRole(Role.CLIENT);
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));

        TrainingPlanCreateRequest request = TrainingPlanCreateRequest.newBuilder()
                .setName("Plan")
                .setTrainerId(1L)
                .build();

        assertThatThrownBy(() -> service.createTrainingPlan(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(trainingPlanRepository, never()).save(any());
    }

    @Test
    void updateTrainingPlan_updatesNameAndDescriptionOnly() {
        User trainer = new User();
        trainer.setId(1L);
        TrainingPlan existing = new TrainingPlan();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setTrainer(trainer);

        when(trainingPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(trainingPlanRepository.save(any(TrainingPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainingPlanRequest request = TrainingPlanRequest.newBuilder()
                .setName("New Name")
                .setDescription("New description")
                .build();

        var response = service.updateTrainingPlan(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getTrainerId()).isEqualTo(1L);
    }

    @Test
    void updateTrainingPlan_throwsWhenMissing() {
        when(trainingPlanRepository.findById(99L)).thenReturn(Optional.empty());

        TrainingPlanRequest request = TrainingPlanRequest.newBuilder().setName("Name").build();

        assertThatThrownBy(() -> service.updateTrainingPlan(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listTrainingPlans_returnsPlansForTrainer() {
        User trainer = new User();
        trainer.setId(1L);
        TrainingPlan plan = new TrainingPlan();
        plan.setId(1L);
        plan.setName("Plan");
        plan.setTrainer(trainer);

        when(trainingPlanRepository.findByTrainerId(1L)).thenReturn(List.of(plan));

        var responses = service.listTrainingPlans(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getTrainerId()).isEqualTo(1L);
        assertThat(responses.getFirst().getDescription()).isEmpty();
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
