package com.vertice.api.trainer;

import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.model.TrainerRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainerServiceTest {

    @Mock
    private TrainerRepository trainerRepository;

    private TrainerService service;

    @BeforeEach
    void setUp() {
        service = new TrainerService(trainerRepository, Mappers.getMapper(TrainerMapper.class));
    }

    @Test
    void createTrainer_rejectsDuplicateEmail() {
        Trainer existing = new Trainer();
        existing.setId(1L);
        existing.setEmail("coach@vertice.com");
        when(trainerRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(existing));

        TrainerRequest request = new TrainerRequest("New Coach", "coach@vertice.com");

        assertThatThrownBy(() -> service.createTrainer(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(trainerRepository, never()).save(any());
    }

    @Test
    void updateTrainer_allowsKeepingOwnEmail() {
        Trainer existing = new Trainer();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setEmail("coach@vertice.com");

        when(trainerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(trainerRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(existing));
        when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainerRequest request = new TrainerRequest("New Name", "coach@vertice.com");

        var response = service.updateTrainer(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("coach@vertice.com");
    }

    @Test
    void updateTrainer_rejectsEmailOwnedByAnotherTrainer() {
        Trainer target = new Trainer();
        target.setId(1L);
        target.setEmail("coach1@vertice.com");

        Trainer other = new Trainer();
        other.setId(2L);
        other.setEmail("coach2@vertice.com");

        when(trainerRepository.findById(1L)).thenReturn(Optional.of(target));
        when(trainerRepository.findByEmail("coach2@vertice.com")).thenReturn(Optional.of(other));

        TrainerRequest request = new TrainerRequest("Coach One", "coach2@vertice.com");

        assertThatThrownBy(() -> service.updateTrainer(1L, request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(trainerRepository, never()).save(any());
    }

    @Test
    void getTrainer_throwsWhenMissing() {
        when(trainerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTrainer(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTrainer_throwsWhenMissing() {
        when(trainerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTrainer(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
