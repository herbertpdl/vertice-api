package com.vertice.api.plan;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanCreateRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanRequest;
import com.vertice.api.generated.grpc.plan.v1.TrainingPlanResponse;
import com.vertice.api.trainer.Trainer;
import com.vertice.api.trainer.TrainerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingPlanMapper trainingPlanMapper;
    private final TrainerRepository trainerRepository;

    @Transactional(readOnly = true)
    public List<TrainingPlanResponse> listTrainingPlans(Long trainerId) {
        return trainingPlanRepository.findByTrainerId(trainerId).stream()
                .map(trainingPlanMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TrainingPlanResponse getTrainingPlan(Long id) {
        return trainingPlanMapper.toResponse(findByIdOrThrow(id));
    }

    public TrainingPlanResponse createTrainingPlan(TrainingPlanCreateRequest request) {
        Trainer trainer = trainerRepository.findById(request.getTrainerId())
                .orElseThrow(() -> new ResourceNotFoundException("Trainer", request.getTrainerId()));
        TrainingPlan trainingPlan = trainingPlanMapper.toEntity(request);
        trainingPlan.setTrainer(trainer);
        return trainingPlanMapper.toResponse(trainingPlanRepository.save(trainingPlan));
    }

    public TrainingPlanResponse updateTrainingPlan(Long id, TrainingPlanRequest request) {
        TrainingPlan trainingPlan = findByIdOrThrow(id);
        trainingPlanMapper.updateEntityFromRequest(request, trainingPlan);
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
}
