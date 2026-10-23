package com.vertice.api.plan.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;

@Data
@Entity
@Table(name = "workout_feedback")
public class WorkoutFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_log_id", nullable = false)
    private WorkoutLog workoutLog;

    /** '' when the client only rated the workout. */
    @Column(nullable = false)
    private String text;

    /** Null on feedback given before ratings existed. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FeedbackRating rating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** The client's idempotency key for this feedback; null when it sent none. */
    @Column(name = "client_key", length = 64)
    private String clientKey;
}
