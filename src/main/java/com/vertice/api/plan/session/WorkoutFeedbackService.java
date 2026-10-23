package com.vertice.api.plan.session;

import com.vertice.api.common.exception.BusinessRuleViolationException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.time.DbInstants;
import com.vertice.api.generated.grpc.session.v1.SubmitWorkoutFeedbackRequest;
import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutFeedbackService {

    /**
     * Feedback entries per workout session (P1-09). Each one is up to 2 000 characters and lands
     * in the trainer's unpaginated {@code ListWorkoutFeedback} and the client's export, so a
     * client can't grow either without bound by resubmitting.
     */
    static final int MAX_FEEDBACK_PER_LOG = 10;

    private final WorkoutFeedbackRepository workoutFeedbackRepository;
    private final WorkoutFeedbackMapper workoutFeedbackMapper;
    private final WorkoutLogRepository workoutLogRepository;

    public WorkoutFeedbackResponse submitWorkoutFeedback(SubmitWorkoutFeedbackRequest request) {
        WorkoutLog workoutLog = workoutLogRepository.findById(request.getWorkoutLogId())
                .orElseThrow(() -> new ResourceNotFoundException("WorkoutLog", request.getWorkoutLogId()));
        if (workoutLog.getStatus() != WorkoutLogStatus.COMPLETED) {
            throw new BusinessRuleViolationException("WorkoutLog %d is not completed".formatted(workoutLog.getId()));
        }
        // An automatic finish (the weekly rollover) takes no feedback (R27, R70).
        if (workoutLog.getCompletionKind() == CompletionKind.AUTO) {
            throw new BusinessRuleViolationException(
                    "WorkoutLog %d was finished automatically and takes no feedback".formatted(workoutLog.getId()));
        }
        if (workoutFeedbackRepository.countByWorkoutLogId(workoutLog.getId()) >= MAX_FEEDBACK_PER_LOG) {
            throw new ConstraintViolationException(
                    "workoutLogId: a workout session takes at most %d feedback entries".formatted(MAX_FEEDBACK_PER_LOG), Set.of());
        }

        WorkoutFeedback feedback = new WorkoutFeedback();
        feedback.setWorkoutLog(workoutLog);
        feedback.setText(request.getText().strip());
        feedback.setRating(WorkoutFeedbackMapper.fromProto(request.getRating()));
        feedback.setCreatedAt(DbInstants.now());
        return workoutFeedbackMapper.toResponse(workoutFeedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public List<WorkoutFeedbackResponse> listWorkoutFeedback(Long trainerId) {
        return workoutFeedbackRepository.findByWorkoutLog_Workout_TrainingPlan_TrainerId(trainerId).stream()
                .map(workoutFeedbackMapper::toResponse)
                .toList();
    }
}
