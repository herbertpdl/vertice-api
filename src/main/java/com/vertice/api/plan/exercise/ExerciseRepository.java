package com.vertice.api.plan.exercise;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /** The starter set plus {@code ownerId}'s private exercises. */
    List<Exercise> findByOwnerIdIsNullOrOwnerId(Long ownerId);
}
