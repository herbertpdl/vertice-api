package com.vertice.api.trainer;

import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.trainer.v1.TrainerCreateRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private TrainerService service;

    @BeforeEach
    void setUp() {
        service = new TrainerService(trainerRepository, Mappers.getMapper(TrainerMapper.class), passwordEncoder);
    }

    @Test
    void createTrainer_hashesPasswordBeforeSaving() {
        when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainerCreateRequest request = TrainerCreateRequest.newBuilder()
                .setName("New Coach")
                .setEmail("coach@vertice.com")
                .setPassword("supersecret1")
                .setCpf("11144477735")
                .build();

        service.createTrainer(request);

        var captor = org.mockito.ArgumentCaptor.forClass(Trainer.class);
        verify(trainerRepository).save(captor.capture());
        Trainer saved = captor.getValue();

        assertThat(saved.getPasswordHash()).isNotEqualTo("supersecret1");
        assertThat(passwordEncoder.matches("supersecret1", saved.getPasswordHash())).isTrue();
    }

    @Test
    void createTrainer_mapsCrefWhenProvided() {
        when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainerCreateRequest request = TrainerCreateRequest.newBuilder()
                .setName("New Coach")
                .setEmail("coach@vertice.com")
                .setPassword("supersecret1")
                .setCpf("11144477735")
                .setCref("123456-G/SP")
                .build();

        var captor = org.mockito.ArgumentCaptor.forClass(Trainer.class);
        service.createTrainer(request);
        verify(trainerRepository).save(captor.capture());

        assertThat(captor.getValue().getCref()).isEqualTo("123456-G/SP");
    }

    @Test
    void createTrainer_leavesCrefBlankWhenOmitted() {
        when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainerCreateRequest request = TrainerCreateRequest.newBuilder()
                .setName("New Coach")
                .setEmail("coach@vertice.com")
                .setPassword("supersecret1")
                .setCpf("11144477735")
                .build();

        var captor = org.mockito.ArgumentCaptor.forClass(Trainer.class);
        service.createTrainer(request);
        verify(trainerRepository).save(captor.capture());

        assertThat(captor.getValue().getCref()).isEmpty();
    }

    @Test
    void updateTrainer_updatesCref() {
        Trainer existing = new Trainer();
        existing.setId(1L);
        existing.setName("Coach");
        existing.setEmail("coach@vertice.com");
        existing.setCpf("11144477735");

        when(trainerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(trainerRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(existing));
        when(trainerRepository.findByCpf("11144477735")).thenReturn(Optional.of(existing));
        when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainerRequest request = TrainerRequest.newBuilder()
                .setName("Coach")
                .setEmail("coach@vertice.com")
                .setCpf("11144477735")
                .setCref("123456-G/SP")
                .build();

        var response = service.updateTrainer(1L, request);

        assertThat(response.getCref()).isEqualTo("123456-G/SP");
    }

    @Test
    void createTrainer_rejectsDuplicateEmail() {
        Trainer existing = new Trainer();
        existing.setId(1L);
        existing.setEmail("coach@vertice.com");
        when(trainerRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(existing));

        TrainerCreateRequest request = TrainerCreateRequest.newBuilder()
                .setName("New Coach")
                .setEmail("coach@vertice.com")
                .setPassword("supersecret1")
                .setCpf("11144477735")
                .build();

        assertThatThrownBy(() -> service.createTrainer(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(trainerRepository, never()).save(any());
    }

    @Test
    void createTrainer_rejectsDuplicateCpf() {
        Trainer existing = new Trainer();
        existing.setId(1L);
        existing.setCpf("11144477735");
        when(trainerRepository.findByCpf("11144477735")).thenReturn(Optional.of(existing));

        TrainerCreateRequest request = TrainerCreateRequest.newBuilder()
                .setName("New Coach")
                .setEmail("coach@vertice.com")
                .setPassword("supersecret1")
                .setCpf("11144477735")
                .build();

        assertThatThrownBy(() -> service.createTrainer(request))
                .isInstanceOf(DuplicateCpfException.class);
        verify(trainerRepository, never()).save(any());
    }

    @Test
    void updateTrainer_allowsKeepingOwnEmailAndCpf() {
        Trainer existing = new Trainer();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setEmail("coach@vertice.com");
        existing.setCpf("11144477735");
        existing.setPasswordHash("$2a$10$existingHash");

        when(trainerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(trainerRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(existing));
        when(trainerRepository.findByCpf("11144477735")).thenReturn(Optional.of(existing));
        when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> inv.getArgument(0));

        TrainerRequest request = TrainerRequest.newBuilder()
                .setName("New Name")
                .setEmail("coach@vertice.com")
                .setCpf("11144477735")
                .build();

        var response = service.updateTrainer(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEmail()).isEqualTo("coach@vertice.com");
        assertThat(response.getCpf()).isEqualTo("11144477735");
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$existingHash");
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

        TrainerRequest request = TrainerRequest.newBuilder()
                .setName("Coach One")
                .setEmail("coach2@vertice.com")
                .build();

        assertThatThrownBy(() -> service.updateTrainer(1L, request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(trainerRepository, never()).save(any());
    }

    @Test
    void updateTrainer_rejectsCpfOwnedByAnotherTrainer() {
        Trainer target = new Trainer();
        target.setId(1L);
        target.setEmail("coach1@vertice.com");
        target.setCpf("52998224725");

        Trainer other = new Trainer();
        other.setId(2L);
        other.setEmail("coach1@vertice.com");
        other.setCpf("11144477735");

        when(trainerRepository.findById(1L)).thenReturn(Optional.of(target));
        when(trainerRepository.findByEmail("coach1@vertice.com")).thenReturn(Optional.of(target));
        when(trainerRepository.findByCpf("11144477735")).thenReturn(Optional.of(other));

        TrainerRequest request = TrainerRequest.newBuilder()
                .setName("Coach One")
                .setEmail("coach1@vertice.com")
                .setCpf("11144477735")
                .build();

        assertThatThrownBy(() -> service.updateTrainer(1L, request))
                .isInstanceOf(DuplicateCpfException.class);
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

    @Test
    void setPassword_hashesAndSaves() {
        Trainer existing = new Trainer();
        existing.setId(1L);
        existing.setPasswordHash("$2a$10$oldHash");

        when(trainerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(trainerRepository.save(any(Trainer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setPassword(1L, "brandNewPassword1");

        assertThat(existing.getPasswordHash()).isNotEqualTo("$2a$10$oldHash");
        assertThat(existing.getPasswordHash()).isNotEqualTo("brandNewPassword1");
        assertThat(passwordEncoder.matches("brandNewPassword1", existing.getPasswordHash())).isTrue();
    }

    @Test
    void passwordEncoder_saltsSamePasswordDifferently() {
        String hash1 = passwordEncoder.encode("samePassword1");
        String hash2 = passwordEncoder.encode("samePassword1");

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(passwordEncoder.matches("samePassword1", hash1)).isTrue();
        assertThat(passwordEncoder.matches("samePassword1", hash2)).isTrue();
    }

    @Test
    void setPassword_throwsWhenMissing() {
        when(trainerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPassword(99L, "brandNewPassword1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
