package com.vertice.api.user;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Matches through V25's unique index on {@code lower(email)}; pass an already normalized email.
     * Never a deleted account (it has no email anyway since V30; the filter keeps that explicit).
     */
    @Query("select u from User u where lower(u.email) = :email and u.deletedAt is null")
    Optional<User> findByEmail(String email);

    Optional<User> findByCpf(String cpf);

    /**
     * Just the role, for the per-call check that a token's user still exists with that role. A
     * deleted account (P1-05) counts as missing, so its tokens stop working on the next call.
     */
    @Query("select u.role from User u where u.id = :id and u.deletedAt is null")
    Optional<Role> findRoleById(Long id);

    /**
     * One {@code ListUsers} page: keyset over the primary key, so pages stay stable under inserts.
     * Deleted accounts are left out: an anonymised row has nothing for ops or the dev login to use.
     */
    List<User> findByDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);

    List<User> findByRoleAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(Role role, Long afterId, Limit limit);

    /** A deleted account has no password either, but must never be offered a setup link. */
    List<User> findByPasswordHashIsNullAndDeletedAtIsNull();

    List<User> findByRoleAndPasswordHashIsNullAndDeletedAtIsNull(Role role);

    /** {@code trainerId}'s active clients who haven't set a password yet. */
    @Query("""
            select tc.client from TrainerClient tc
            where tc.trainer.id = :trainerId
              and tc.endedAt is null
              and tc.client.role = com.vertice.api.user.Role.CLIENT
              and tc.client.passwordHash is null
            """)
    List<User> findActiveClientsWithoutPassword(Long trainerId);

    /**
     * Whether any row that has no {@code ON DELETE} rule points at the user: a plan they wrote or
     * that was written for them, a workout log of theirs, a relationship as the trainer (ended
     * ones too) or a private exercise. Their own {@code trainer_clients} rows as the client and
     * their password setup tokens cascade, so they don't count.
     */
    @Query(nativeQuery = true, value = """
            SELECT EXISTS (SELECT 1 FROM training_plans WHERE trainer_id = :id OR client_id = :id)
                OR EXISTS (SELECT 1 FROM workout_logs WHERE client_id = :id)
                OR EXISTS (SELECT 1 FROM trainer_clients WHERE trainer_id = :id)
                OR EXISTS (SELECT 1 FROM exercises WHERE owner_id = :id)
            """)
    boolean hasTrainingData(Long id);
}
