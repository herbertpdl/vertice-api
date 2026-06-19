package com.vertice.api.plan.exercise;

import com.vertice.api.plan.TrainingPlan;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private Integer sets;

    private Integer reps;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_plan_id", nullable = false)
    private TrainingPlan trainingPlan;
}
