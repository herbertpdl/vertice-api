package com.vertice.api.user;

import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_CPF = "11144477735";
    private static final String OTHER_VALID_CPF = "52998224725";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, Mappers.getMapper(UserMapper.class), passwordEncoder);
    }

    @Test
    void createUser_hashesPasswordBeforeSaving() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserCreateRequest request = UserCreateRequest.newBuilder()
                .setName("New Coach").setEmail("coach@vertice.com").setPassword("supersecret1")
                .setCpf(VALID_CPF).setRole(Role.TRAINER).build();

        service.createUser(request);

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPasswordHash()).isNotEqualTo("supersecret1");
        assertThat(passwordEncoder.matches("supersecret1", saved.getPasswordHash())).isTrue();
        assertThat(saved.getRole()).isEqualTo(com.vertice.api.user.Role.TRAINER);
    }

    @Test
    void createUser_mapsCrefForTrainer() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserCreateRequest request = UserCreateRequest.newBuilder()
                .setName("New Coach").setEmail("coach@vertice.com").setPassword("supersecret1")
                .setCpf(VALID_CPF).setRole(Role.TRAINER).setCref("123456-G/SP").build();

        service.createUser(request);

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getCref()).isEqualTo("123456-G/SP");
    }

    @Test
    void createUser_rejectsCrefForNonTrainer() {
        UserCreateRequest request = UserCreateRequest.newBuilder()
                .setName("New Client").setEmail("client@vertice.com").setPassword("supersecret1")
                .setCpf(VALID_CPF).setRole(Role.CLIENT).setCref("123456-G/SP").build();

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(ConstraintViolationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rejectsDuplicateEmail() {
        User existing = new User();
        existing.setId(1L);
        existing.setEmail("coach@vertice.com");
        when(userRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(existing));

        UserCreateRequest request = UserCreateRequest.newBuilder()
                .setName("New Coach").setEmail("coach@vertice.com").setPassword("supersecret1")
                .setCpf(VALID_CPF).setRole(Role.TRAINER).build();

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rejectsDuplicateEmailAcrossRoles() {
        User existingTrainer = new User();
        existingTrainer.setId(1L);
        existingTrainer.setEmail("shared@vertice.com");
        existingTrainer.setRole(com.vertice.api.user.Role.TRAINER);
        when(userRepository.findByEmail("shared@vertice.com")).thenReturn(Optional.of(existingTrainer));

        UserCreateRequest request = UserCreateRequest.newBuilder()
                .setName("New Client").setEmail("shared@vertice.com").setPassword("supersecret1")
                .setCpf(OTHER_VALID_CPF).setRole(Role.CLIENT).build();

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rejectsDuplicateCpf() {
        User existing = new User();
        existing.setId(1L);
        existing.setCpf(VALID_CPF);
        when(userRepository.findByCpf(VALID_CPF)).thenReturn(Optional.of(existing));

        UserCreateRequest request = UserCreateRequest.newBuilder()
                .setName("New Coach").setEmail("coach@vertice.com").setPassword("supersecret1")
                .setCpf(VALID_CPF).setRole(Role.TRAINER).build();

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(DuplicateCpfException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_allowsKeepingOwnEmailAndCpf() {
        User existing = new User();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setEmail("coach@vertice.com");
        existing.setCpf(VALID_CPF);
        existing.setRole(com.vertice.api.user.Role.TRAINER);
        existing.setPasswordHash("$2a$10$existingHash");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(existing));
        when(userRepository.findByCpf(VALID_CPF)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRequest request = UserRequest.newBuilder()
                .setName("New Name").setEmail("coach@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();

        var response = service.updateUser(1L, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$existingHash");
    }

    @Test
    void updateUser_rejectsEmailOwnedByAnotherUser() {
        User target = new User();
        target.setId(1L);
        target.setEmail("coach1@vertice.com");

        User other = new User();
        other.setId(2L);
        other.setEmail("coach2@vertice.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(userRepository.findByEmail("coach2@vertice.com")).thenReturn(Optional.of(other));

        UserRequest request = UserRequest.newBuilder()
                .setName("Coach One").setEmail("coach2@vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build();

        assertThatThrownBy(() -> service.updateUser(1L, request))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void listUsers_withRoleFilter_returnsOnlyThatRole() {
        User trainer = new User();
        trainer.setId(1L);
        trainer.setName("Coach");
        trainer.setEmail("coach@vertice.com");
        trainer.setCpf(VALID_CPF);
        trainer.setRole(com.vertice.api.user.Role.TRAINER);
        when(userRepository.findByRole(com.vertice.api.user.Role.TRAINER)).thenReturn(List.of(trainer));

        var responses = service.listUsers(com.vertice.api.user.Role.TRAINER);

        assertThat(responses).hasSize(1);
    }

    @Test
    void listUsers_withoutFilter_returnsAll() {
        User trainer = new User();
        trainer.setId(1L);
        trainer.setName("Coach");
        trainer.setEmail("coach@vertice.com");
        trainer.setCpf(VALID_CPF);
        trainer.setRole(com.vertice.api.user.Role.TRAINER);

        User client = new User();
        client.setId(2L);
        client.setName("Client");
        client.setEmail("client@vertice.com");
        client.setCpf(OTHER_VALID_CPF);
        client.setRole(com.vertice.api.user.Role.CLIENT);

        when(userRepository.findAll()).thenReturn(List.of(trainer, client));

        var responses = service.listUsers(null);

        assertThat(responses).hasSize(2);
    }

    @Test
    void getUser_throwsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUser_throwsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setPassword_hashesAndSaves() {
        User existing = new User();
        existing.setId(1L);
        existing.setPasswordHash("$2a$10$oldHash");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setPassword(1L, "brandNewPassword1");

        assertThat(existing.getPasswordHash()).isNotEqualTo("$2a$10$oldHash");
        assertThat(passwordEncoder.matches("brandNewPassword1", existing.getPasswordHash())).isTrue();
    }

    @Test
    void setPassword_throwsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPassword(99L, "brandNewPassword1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
