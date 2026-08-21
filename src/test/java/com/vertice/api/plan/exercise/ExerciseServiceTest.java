package com.vertice.api.plan.exercise;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.exercise.v1.ExerciseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    private ExerciseService service;

    @BeforeEach
    void setUp() {
        service = new ExerciseService(exerciseRepository, Mappers.getMapper(ExerciseMapper.class));
    }

    @Test
    void createExercise_savesAndReturnsResponse() {
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        ExerciseRequest request = ExerciseRequest.newBuilder()
                .setName("Bench Press")
                .setDescription("Barbell flat bench press")
                .build();

        var response = service.createExercise(request);

        assertThat(response.getName()).isEqualTo("Bench Press");
        assertThat(response.getDescription()).isEqualTo("Barbell flat bench press");
    }

    @Test
    void updateExercise_updatesNameAndDescription() {
        Exercise existing = new Exercise();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setDescription("Old description");

        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        ExerciseRequest request = ExerciseRequest.newBuilder()
                .setName("New Name")
                .setDescription("New description")
                .build();

        var response = service.updateExercise(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getDescription()).isEqualTo("New description");
    }

    @Test
    void updateExercise_throwsWhenMissing() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        ExerciseRequest request = ExerciseRequest.newBuilder().setName("Name").build();

        assertThatThrownBy(() -> service.updateExercise(99L, request))
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
}
