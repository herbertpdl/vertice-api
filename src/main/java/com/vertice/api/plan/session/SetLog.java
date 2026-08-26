package com.vertice.api.plan.session;

import com.vertice.api.plan.workout.ExerciseSet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "set_logs")
public class SetLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_log_id", nullable = false)
    private WorkoutLog workoutLog;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_set_id", nullable = false)
    private ExerciseSet exerciseSet;

    @Column(precision = 6, scale = 2)
    private BigDecimal weight;

    private Integer reps;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
