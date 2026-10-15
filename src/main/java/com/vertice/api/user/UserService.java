package com.vertice.api.user;

import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.HasRecordedDataException;
import com.vertice.api.common.exception.InvalidCredentialsException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.time.DbInstants;
import com.vertice.api.generated.grpc.user.v1.ConsentState;
import com.vertice.api.generated.grpc.user.v1.ConsentType;
import com.vertice.api.generated.grpc.user.v1.ListUsersResponse;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    /** Largest {@code ListUsers} page; also the page size when the request leaves it at 0. */
    public static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordSetupTokenRepository passwordSetupTokenRepository;
    /**
     * Compared against when there's no real hash to check (unknown email, no password set), so
     * those cases cost the same bcrypt round as a wrong password and timing doesn't reveal which
     * emails exist. Encoded once, with the same encoder, so it has the same cost factor.
     */
    private final String dummyPasswordHash;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder,
                       PasswordSetupTokenRepository passwordSetupTokenRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordSetupTokenRepository = passwordSetupTokenRepository;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * One page of users ordered by id, in the {@link UserView#CONTACT} view.
     *
     * @param pageSize already bounded by the caller (1 to {@link #MAX_PAGE_SIZE})
     * @param afterId  the last id of the previous page, 0 for the first page
     */
    @Transactional(readOnly = true)
    public ListUsersResponse listUsers(com.vertice.api.user.Role roleFilter, int pageSize, long afterId) {
        // One extra row tells whether another page follows without a count query.
        Limit limit = Limit.of(pageSize + 1);
        List<User> users = roleFilter == null
                ? userRepository.findByDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(afterId, limit)
                : userRepository.findByRoleAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(roleFilter, afterId, limit);
        boolean hasNext = users.size() > pageSize;
        List<User> page = hasNext ? users.subList(0, pageSize) : users;
        ListUsersResponse.Builder response = ListUsersResponse.newBuilder()
                .addAllUsers(page.stream().map(user -> UserView.CONTACT.restrict(userMapper.toResponse(user))).toList());
        if (hasNext) {
            response.setNextPageToken(String.valueOf(page.getLast().getId()));
        }
        return response.build();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return userMapper.toResponse(findByIdOrThrow(id));
    }

    /** In the {@link UserView#SUMMARY} view: the BFF only needs the ids. */
    @Transactional(readOnly = true)
    public List<UserResponse> listUsersWithoutPassword(com.vertice.api.user.Role roleFilter) {
        List<User> users = roleFilter == null
                ? userRepository.findByPasswordHashIsNullAndDeletedAtIsNull()
                : userRepository.findByRoleAndPasswordHashIsNullAndDeletedAtIsNull(roleFilter);
        return summaries(users);
    }

    /** {@link #listUsersWithoutPassword} narrowed to {@code trainerId}'s active clients. */
    @Transactional(readOnly = true)
    public List<UserResponse> listClientsWithoutPassword(Long trainerId, com.vertice.api.user.Role roleFilter) {
        if (roleFilter != null && roleFilter != com.vertice.api.user.Role.CLIENT) {
            return List.of();
        }
        return summaries(userRepository.findActiveClientsWithoutPassword(trainerId));
    }

    private List<UserResponse> summaries(List<User> users) {
        return users.stream().map(user -> UserView.SUMMARY.restrict(userMapper.toResponse(user))).toList();
    }

    /**
     * An empty password creates the user without one; they set it later with a setup token. A
     * non-empty {@code accepted_terms_version} is recorded as accepted now.
     */
    public UserResponse createUser(UserCreateRequest request) {
        String email = Emails.normalize(request.getEmail());
        assertEmailNotReserved(email);
        assertEmailAvailable(email, null);
        assertCpfRequiredForTrainer(request.getCpf(), request.getRole());
        assertCpfAvailable(request.getCpf(), null);
        assertCrefOnlyForTrainer(request.getCref(), request.getRole());
        User user = userMapper.toEntity(request);
        user.setEmail(email);
        user.setPasswordHash(request.getPassword().isEmpty() ? null : passwordEncoder.encode(request.getPassword()));
        if (!request.getAcceptedTermsVersion().isEmpty()) {
            user.acceptTerms(request.getAcceptedTermsVersion(), DbInstants.now());
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    public UserResponse updateUser(Long id, UserRequest request) {
        User user = findByIdOrThrow(id);
        String email = Emails.normalize(request.getEmail());
        assertEmailNotReserved(email);
        assertEmailAvailable(email, id);
        assertCpfRequiredForTrainer(request.getCpf(), request.getRole());
        assertCpfAvailable(request.getCpf(), id);
        assertCrefOnlyForTrainer(request.getCref(), request.getRole());
        userMapper.updateEntityFromRequest(request, user);
        user.setEmail(email);
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Every failure — unknown email, no password set, wrong password — throws the same
     * {@link InvalidCredentialsException} after one bcrypt comparison, so neither the response nor
     * its timing tells them apart.
     */
    @Transactional(readOnly = true)
    public UserResponse verifyCredentials(String email, String rawPassword) {
        Optional<User> user = userRepository.findByEmail(Emails.normalize(email));
        String storedHash = user.map(User::getPasswordHash).filter(hash -> !hash.isBlank()).orElse(null);
        boolean matches = passwordEncoder.matches(rawPassword, storedHash != null ? storedHash : dummyPasswordHash);
        if (storedHash == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return userMapper.toResponse(user.get());
    }

    /** Records {@code type} as consented to now, in {@code version}, replacing any earlier record. */
    public ConsentState recordConsent(Long userId, ConsentType type, String version) {
        User user = findByIdOrThrow(userId);
        switch (type) {
            case TERMS -> user.acceptTerms(version, DbInstants.now());
            case HEALTH_DATA -> user.consentToHealthData(version, DbInstants.now());
            // The controller refuses both before calling.
            case CONSENT_TYPE_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalArgumentException("consent type must be set");
        }
        return userMapper.toConsentState(userRepository.save(user));
    }

    /**
     * Refused while the user has training data (REL-API-003): deleting them would break a foreign
     * key. Removing an account with data is anonymisation, a separate path (P1-05).
     */
    public void deleteUser(Long id) {
        User user = findByIdOrThrow(id);
        if (userRepository.hasTrainingData(id)) {
            throw new HasRecordedDataException("User", id, "training plans, workout logs, clients or exercises");
        }
        userRepository.delete(user);
    }

    /** A password set directly makes any pending setup link pointless, so those are revoked. */
    public void setPassword(Long id, String rawPassword) {
        User user = findByIdOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        passwordSetupTokenRepository.deleteUnusedByUserId(id);
    }

    /**
     * A deleted account (P1-05) is missing for every RPC here: GetUser, UpdateUser, RecordConsent,
     * SetUserPassword and DeleteUser are NOT_FOUND on it, ADMIN included, so nothing can be written
     * back into an anonymised row.
     */
    private User findByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private static void assertEmailNotReserved(String email) {
        if (Emails.isReserved(email)) {
            throw new ConstraintViolationException("email: addresses on the .invalid domain are reserved", Set.of());
        }
    }

    private void assertEmailAvailable(String email, Long excludingId) {
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateEmailException();
                });
    }

    /** Users without a CPF never collide (V27's partial unique index only covers set values). */
    private void assertCpfAvailable(String cpf, Long excludingId) {
        if (cpf.isEmpty()) {
            return;
        }
        userRepository.findByCpf(cpf)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateCpfException();
                });
    }

    /**
     * A TRAINER needs a CPF (identity, CREF checks and invoicing); a CLIENT doesn't (data
     * minimisation, P1-04 — pending legal review, see docs/data-inventory.md). V27's
     * {@code ck_users_trainer_cpf} is the database backstop.
     */
    private void assertCpfRequiredForTrainer(String cpf, Role role) {
        if (cpf.isEmpty() && role == Role.TRAINER) {
            throw new ConstraintViolationException("cpf: required for TRAINER-role users", Set.of());
        }
    }

    /**
     * {@code cref} (the CREF professional registration number) only makes sense for a TRAINER —
     * there's no dedicated trainer table anymore to hold it exclusively, so this is enforced here
     * instead. See docs/specs/user-unification/spec.md section 3.2.
     */
    private void assertCrefOnlyForTrainer(String cref, Role role) {
        if (!cref.isBlank() && role != Role.TRAINER) {
            throw new ConstraintViolationException("cref may only be set for TRAINER-role users", Set.of());
        }
    }
}
