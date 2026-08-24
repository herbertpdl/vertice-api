package com.vertice.api.user;

import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(com.vertice.api.user.Role roleFilter) {
        List<User> users = roleFilter == null ? userRepository.findAll() : userRepository.findByRole(roleFilter);
        return users.stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return userMapper.toResponse(findByIdOrThrow(id));
    }

    public UserResponse createUser(UserCreateRequest request) {
        assertEmailAvailable(request.getEmail(), null);
        assertCpfAvailable(request.getCpf(), null);
        assertCrefOnlyForTrainer(request.getCref(), request.getRole());
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return userMapper.toResponse(userRepository.save(user));
    }

    public UserResponse updateUser(Long id, UserRequest request) {
        User user = findByIdOrThrow(id);
        assertEmailAvailable(request.getEmail(), id);
        assertCpfAvailable(request.getCpf(), id);
        assertCrefOnlyForTrainer(request.getCref(), request.getRole());
        userMapper.updateEntityFromRequest(request, user);
        return userMapper.toResponse(userRepository.save(user));
    }

    public void deleteUser(Long id) {
        User user = findByIdOrThrow(id);
        userRepository.delete(user);
    }

    public void setPassword(Long id, String rawPassword) {
        User user = findByIdOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
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
