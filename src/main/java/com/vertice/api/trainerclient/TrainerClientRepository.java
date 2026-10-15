package com.vertice.api.trainerclient;

import com.vertice.api.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainerClientRepository extends JpaRepository<TrainerClient, Long> {

    List<TrainerClient> findByTrainerIdAndEndedAtIsNull(Long trainerId);

    boolean existsByTrainerIdAndEndedAtIsNull(Long trainerId);

    boolean existsByTrainerIdAndClientIdAndEndedAtIsNull(Long trainerId, Long clientId);

    Optional<TrainerClient> findByTrainerIdAndClientIdAndEndedAtIsNull(Long trainerId, Long clientId);

    /** Whether {@code clientId} is, right now, a CLIENT-role user on {@code trainerId}'s roster. */
    boolean existsByTrainerIdAndClientIdAndClientRoleAndEndedAtIsNull(Long trainerId, Long clientId, Role clientRole);
}
