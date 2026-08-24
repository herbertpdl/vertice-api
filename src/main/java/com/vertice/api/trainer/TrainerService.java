package com.vertice.api.trainer;

import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.trainer.v1.TrainerCreateRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainerService {

    private final TrainerRepository trainerRepository;
    private final TrainerMapper trainerMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<TrainerResponse> listTrainers() {
        return trainerRepository.findAll().stream()
                .map(trainerMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TrainerResponse getTrainer(Long id) {
        return trainerMapper.toResponse(findByIdOrThrow(id));
    }

    public TrainerResponse createTrainer(TrainerCreateRequest request) {
        assertEmailAvailable(request.getEmail(), null);
        assertCpfAvailable(request.getCpf(), null);
        Trainer trainer = trainerMapper.toEntity(request);
        trainer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return trainerMapper.toResponse(trainerRepository.save(trainer));
    }

    public TrainerResponse updateTrainer(Long id, TrainerRequest request) {
        Trainer trainer = findByIdOrThrow(id);
        assertEmailAvailable(request.getEmail(), id);
        assertCpfAvailable(request.getCpf(), id);
        trainerMapper.updateEntityFromRequest(request, trainer);
        return trainerMapper.toResponse(trainerRepository.save(trainer));
    }

    public void deleteTrainer(Long id) {
        Trainer trainer = findByIdOrThrow(id);
        trainerRepository.delete(trainer);
    }

    public void setPassword(Long id, String rawPassword) {
        Trainer trainer = findByIdOrThrow(id);
        trainer.setPasswordHash(passwordEncoder.encode(rawPassword));
        trainerRepository.save(trainer);
    }

    private Trainer findByIdOrThrow(Long id) {
        return trainerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer", id));
    }

    private void assertEmailAvailable(String email, Long excludingId) {
        trainerRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateEmailException(email);
                });
    }

    private void assertCpfAvailable(String cpf, Long excludingId) {
        trainerRepository.findByCpf(cpf)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateCpfException(cpf);
                });
    }
}
