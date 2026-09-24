package com.vertice.api.plan.exercise;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.BatchSize;

/** Reference data seeded by V22 (14 launch groups, ids 1..14 in catalog order); never edited through the API. */
@Data
@Entity
@Table(name = "muscle_groups")
@BatchSize(size = 20)
public class MuscleGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;
}
