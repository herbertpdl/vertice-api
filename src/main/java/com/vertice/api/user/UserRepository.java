package com.vertice.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Matches through V25's unique index on {@code lower(email)}; pass an already normalized email. */
    @Query("select u from User u where lower(u.email) = :email")
    Optional<User> findByEmail(String email);

    Optional<User> findByCpf(String cpf);

    List<User> findByRole(Role role);

    List<User> findByPasswordHashIsNull();

    List<User> findByRoleAndPasswordHashIsNull(Role role);

    /** {@code trainerId}'s active clients who haven't set a password yet. */
    @Query("""
            select tc.client from TrainerClient tc
            where tc.trainer.id = :trainerId
              and tc.endedAt is null
              and tc.client.role = com.vertice.api.user.Role.CLIENT
              and tc.client.passwordHash is null
            """)
    List<User> findActiveClientsWithoutPassword(Long trainerId);
}
