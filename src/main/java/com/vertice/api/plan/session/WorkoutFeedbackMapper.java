package com.vertice.api.plan.session;

import com.vertice.api.generated.grpc.session.v1.WorkoutFeedbackResponse;
import com.vertice.api.grpc.ProtoInstants;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ProtoInstants.class)
public interface WorkoutFeedbackMapper {

    @Mapping(target = "workoutLogId", source = "workoutLog.id")
    @Mapping(target = "workoutId", source = "workoutLog.workout.id")
    @Mapping(target = "trainingPlanId", source = "workoutLog.workout.trainingPlan.id")
    @Mapping(target = "clientId", source = "workoutLog.client.id")
    @Mapping(target = "createdAt", qualifiedByName = "instantToString")
    @Mapping(target = "replaced", expression = "java(workoutFeedback.getWorkoutLog().getStatus() == WorkoutLogStatus.SUPERSEDED)")
    WorkoutFeedbackResponse toResponse(WorkoutFeedback workoutFeedback);

    default com.vertice.api.generated.grpc.session.v1.FeedbackRating toProto(FeedbackRating rating) {
        if (rating == null) {
            return com.vertice.api.generated.grpc.session.v1.FeedbackRating.FEEDBACK_RATING_UNSPECIFIED;
        }
        return switch (rating) {
            case VERY_HARD -> com.vertice.api.generated.grpc.session.v1.FeedbackRating.FEEDBACK_RATING_VERY_HARD;
            case HARD -> com.vertice.api.generated.grpc.session.v1.FeedbackRating.FEEDBACK_RATING_HARD;
            case JUST_RIGHT -> com.vertice.api.generated.grpc.session.v1.FeedbackRating.FEEDBACK_RATING_JUST_RIGHT;
            case EASY -> com.vertice.api.generated.grpc.session.v1.FeedbackRating.FEEDBACK_RATING_EASY;
            case VERY_EASY -> com.vertice.api.generated.grpc.session.v1.FeedbackRating.FEEDBACK_RATING_VERY_EASY;
        };
    }

    /** The request's rating; null for UNSPECIFIED (rejected by the controller's validation). */
    static FeedbackRating fromProto(com.vertice.api.generated.grpc.session.v1.FeedbackRating rating) {
        return switch (rating) {
            case FEEDBACK_RATING_VERY_HARD -> FeedbackRating.VERY_HARD;
            case FEEDBACK_RATING_HARD -> FeedbackRating.HARD;
            case FEEDBACK_RATING_JUST_RIGHT -> FeedbackRating.JUST_RIGHT;
            case FEEDBACK_RATING_EASY -> FeedbackRating.EASY;
            case FEEDBACK_RATING_VERY_EASY -> FeedbackRating.VERY_EASY;
            case FEEDBACK_RATING_UNSPECIFIED, UNRECOGNIZED -> null;
        };
    }
}
