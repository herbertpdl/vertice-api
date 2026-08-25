package com.vertice.api.plan.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SetLogRepository extends JpaRepository<SetLog, Long> {

    List<SetLog> findByWorkoutLogId(Long workoutLogId);

    Optional<SetLog> findByWorkoutLogIdAndExerciseSetId(Long workoutLogId, Long exerciseSetId);
}
