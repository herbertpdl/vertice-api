package com.vertice.api.user;

import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.InvalidCredentialsException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import jakarta.validation.ConstraintViolationException;
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

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(com.vertice.api.user.Role roleFilter) {
        List<User> users = roleFilter == null ? userRepository.findAll() : userRepository.findByRole(roleFilter);
        return users.stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return userMapper.toResponse(findByIdOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsersWithoutPassword(com.vertice.api.user.Role roleFilter) {
        List<User> users = roleFilter == null
                ? userRepository.findByPasswordHashIsNull()
                : userRepository.findByRoleAndPasswordHashIsNull(roleFilter);
        return users.stream().map(userMapper::toResponse).toList();
    }

    /** An empty password creates the user without one; they set it later with a setup token. */
    public UserResponse createUser(UserCreateRequest request) {
        String email = Emails.normalize(request.getEmail());
        assertEmailAvailable(email, null);
        assertCpfAvailable(request.getCpf(), null);
        assertCrefOnlyForTrainer(request.getCref(), request.getRole());
        User user = userMapper.toEntity(request);
        user.setEmail(email);
        user.setPasswordHash(request.getPassword().isEmpty() ? null : passwordEncoder.encode(request.getPassword()));
        return userMapper.toResponse(userRepository.save(user));
    }

    public UserResponse updateUser(Long id, UserRequest request) {
        User user = findByIdOrThrow(id);
        String email = Emails.normalize(request.getEmail());
        assertEmailAvailable(email, id);
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

    public void deleteUser(Long id) {
        User user = findByIdOrThrow(id);
        userRepository.delete(user);
    }

    /** A password set directly makes any pending setup link pointless, so those are revoked. */
    public void setPassword(Long id, String rawPassword) {
        User user = findByIdOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        passwordSetupTokenRepository.deleteUnusedByUserId(id);
    }

    private User findByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private void assertEmailAvailable(String email, Long excludingId) {
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateEmailException(email);
                });
    }

    private void assertCpfAvailable(String cpf, Long excludingId) {
        userRepository.findByCpf(cpf)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateCpfException(cpf);
                });
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
