package com.vertice.api.trainerclient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainerClientRepository extends JpaRepository<TrainerClient, Long> {

    List<TrainerClient> findByTrainerIdAndEndedAtIsNull(Long trainerId);

    boolean existsByTrainerIdAndClientIdAndEndedAtIsNull(Long trainerId, Long clientId);
}
