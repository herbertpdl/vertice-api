package com.vertice.api.plan.exercise;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MuscleGroupRepository extends JpaRepository<MuscleGroup, Long> {

    List<MuscleGroup> findAllByOrderByIdAsc();
}
