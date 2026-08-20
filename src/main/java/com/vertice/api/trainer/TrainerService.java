package com.vertice.api.trainer;

import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.model.TrainerRequest;
import com.vertice.api.generated.model.TrainerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainerService {

    private final TrainerRepository trainerRepository;
    private final TrainerMapper trainerMapper;

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

    public TrainerResponse createTrainer(TrainerRequest request) {
        assertEmailAvailable(request.getEmail(), null);
        Trainer trainer = trainerMapper.toEntity(request);
        return trainerMapper.toResponse(trainerRepository.save(trainer));
    }

    public TrainerResponse updateTrainer(Long id, TrainerRequest request) {
        Trainer trainer = findByIdOrThrow(id);
        assertEmailAvailable(request.getEmail(), id);
        trainerMapper.updateEntityFromRequest(request, trainer);
        return trainerMapper.toResponse(trainerRepository.save(trainer));
    }

    public void deleteTrainer(Long id) {
        Trainer trainer = findByIdOrThrow(id);
        trainerRepository.delete(trainer);
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
}
