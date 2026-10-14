package com.vertice.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, Long> {

    Optional<PasswordSetupToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from PasswordSetupToken t where t.user.id = :userId and t.usedAt is null")
    void deleteUnusedByUserId(Long userId);

    /**
     * Consumes the token in one conditional UPDATE, so two concurrent uses can't both succeed.
     * Returns 0 when the token is unknown, expired or already used. Flushes first so clearing the
     * persistence context afterwards can't drop pending changes.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordSetupToken t
               set t.usedAt = :now
             where t.tokenHash = :tokenHash
               and t.usedAt is null
               and t.expiresAt > :now
            """)
    int markUsed(String tokenHash, Instant now);
}
