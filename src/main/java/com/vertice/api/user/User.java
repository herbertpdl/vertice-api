package com.vertice.api.user;

import com.vertice.api.common.time.DbInstants;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // null for a CLIENT who didn't give one; a TRAINER always has one (UserService, P1-04). Unique
    // when set (V27's partial index).
    @Column(length = 11)
    private String cpf;

    @Column(length = 20)
    private String cref;

    // null until the user sets a password (e.g. a client created by a trainer, see
    // PasswordSetupTokenService); such a user can't pass VerifyCredentials.
    @ToString.Exclude
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Consent records (P1-04, docs/data-inventory.md): the version of the text the user accepted and
    // when. Both null until the user accepts; RecordConsent overwrites them with the latest acceptance.
    @Column(name = "accepted_terms_version", length = 32)
    private String acceptedTermsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "health_data_consent_version", length = 32)
    private String healthDataConsentVersion;

    @Column(name = "health_data_consent_at")
    private Instant healthDataConsentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = DbInstants.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = DbInstants.now();
    }

    public void acceptTerms(String version, Instant at) {
        acceptedTermsVersion = version;
        termsAcceptedAt = at;
    }

    public void consentToHealthData(String version, Instant at) {
        healthDataConsentVersion = version;
        healthDataConsentAt = at;
    }
}
