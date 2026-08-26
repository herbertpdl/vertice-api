package com.vertice.api.plan.session;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutFeedbackService {

    private final WorkoutFeedbackRepository workoutFeedbackRepository;
    private final WorkoutFeedbackMapper workoutFeedbackMapper;
    private final WorkoutLogRepository workoutLogRepository;

    public WorkoutFeedbackResponse submitWorkoutFeedback(SubmitWorkoutFeedbackRequest request) {
        WorkoutLog workoutLog = workoutLogRepository.findById(request.getWorkoutLogId())
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutLog", request.getWorkoutLogId()));
        if (workoutLog.getCompletedAt() == null) {
            throw new ConstraintViolationException("workoutLogId: workout session is not completed yet", Set.of());
        }

        WorkoutFeedback feedback = new WorkoutFeedback();
        feedback.setWorkoutLog(workoutLog);
        feedback.setText(request.getText());
        feedback.setCreatedAt(Instant.now());
        return workoutFeedbackMapper.toResponse(workoutFeedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public List<WorkoutFeedbackResponse> listWorkoutFeedback(Long trainerId) {
        return workoutFeedbackRepository.findByWorkoutLog_Workout_TrainingPlan_TrainerId(trainerId).stream()
                .map(workoutFeedbackMapper::toResponse)
                .toList();
    }
}
