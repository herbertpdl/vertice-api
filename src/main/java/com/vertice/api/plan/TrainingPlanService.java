package com.vertice.api.plan;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.grpc.ProtoDates;
import com.vertice.api.user.Role;
import com.vertice.api.user.User;
import com.vertice.api.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingPlanMapper trainingPlanMapper;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TrainingPlanResponse> listTrainingPlans(Long trainerId, Long clientId) {
        List<TrainingPlan> plans;
        if (trainerId != null && clientId != null) {
            plans = trainingPlanRepository.findByTrainerId(trainerId).stream()
                    .filter(plan -> plan.getClient().getId().equals(clientId))
                    .toList();
        } else if (trainerId != null) {
            plans = trainingPlanRepository.findByTrainerId(trainerId);
        } else if (clientId != null) {
            plans = trainingPlanRepository.findByClientId(clientId);
        } else {
            plans = trainingPlanRepository.findAll();
        }
        return plans.stream().map(trainingPlanMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TrainingPlanResponse getTrainingPlan(Long id) {
        return trainingPlanMapper.toResponse(findByIdOrThrow(id));
    }

    public TrainingPlanResponse createTrainingPlan(TrainingPlanCreateRequest request) {
        User trainer = findUserWithRoleOrThrow(request.getTrainerId(), Role.TRAINER, "Trainer");
        User client = findUserWithRoleOrThrow(request.getClientId(), Role.CLIENT, "Client");
        assertDateRangeValid(request.getStartDate(), request.getEndDate());
        TrainingPlan trainingPlan = trainingPlanMapper.toEntity(request);
        trainingPlan.setTrainer(trainer);
        trainingPlan.setClient(client);
        return trainingPlanMapper.toResponse(trainingPlanRepository.save(trainingPlan));
    }

    public TrainingPlanResponse updateTrainingPlan(Long id, TrainingPlanRequest request) {
        TrainingPlan trainingPlan = findByIdOrThrow(id);
        User client = findUserWithRoleOrThrow(request.getClientId(), Role.CLIENT, "Client");
        assertDateRangeValid(request.getStartDate(), request.getEndDate());
        trainingPlanMapper.updateEntityFromRequest(request, trainingPlan);
        trainingPlan.setClient(client);
        return trainingPlanMapper.toResponse(trainingPlanRepository.save(trainingPlan));
    }

    public void deleteTrainingPlan(Long id) {
        TrainingPlan trainingPlan = findByIdOrThrow(id);
        trainingPlanRepository.delete(trainingPlan);
    }

    private TrainingPlan findByIdOrThrow(Long id) {
        return trainingPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingPlan", id));
    }

    private User findUserWithRoleOrThrow(Long id, Role role, String resourceName) {
        return userRepository.findById(id)
                .filter(user -> user.getRole() == role)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
    }

    /**
     * Cross-field ("end >= start") validation can't be expressed as a Bean Validation annotation
     * on a single-field validation record, so it's checked here directly — same manual-check
     * pattern {@code UserService#assertCrefOnlyForTrainer} already uses. Also where malformed
     * date strings surface as {@code INVALID_ARGUMENT} rather than deeper in the mapper, so the
     * error message is specific to which check failed.
     */
    private void assertDateRangeValid(String startDate, String endDate) {
        LocalDate start = ProtoDates.stringToDate(startDate);
        LocalDate end = ProtoDates.stringToDate(endDate);
        if (end.isBefore(start)) {
            throw new ConstraintViolationException("endDate must not be before startDate", Set.of());
        }
    }
}
