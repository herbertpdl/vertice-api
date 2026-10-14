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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_CPF = "11144477735";
    private static final String OTHER_VALID_CPF = "52998224725";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordSetupTokenRepository tokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, Mappers.getMapper(UserMapper.class), passwordEncoder, tokenRepository);
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
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email already in use");
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

        // A fixed message: echoing the CPF would confirm it is registered (PRIV-BFF-002).
        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(DuplicateCpfException.class)
                .hasMessage("CPF already in use")
                .message().doesNotContainPattern("\\d");
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
    void listUsers_withRoleFilter_queriesThatRole_inTheContactView() {
        User trainer = user(1L, com.vertice.api.user.Role.TRAINER, "coach@vertice.com", VALID_CPF);
        trainer.setCref("123456-G/SP");
        when(userRepository.findByRoleAndIdGreaterThanOrderByIdAsc(com.vertice.api.user.Role.TRAINER, 0L, Limit.of(11)))
                .thenReturn(List.of(trainer));

        var page = service.listUsers(com.vertice.api.user.Role.TRAINER, 10, 0L);

        assertThat(page.getUsersList()).containsExactly(UserResponse.newBuilder()
                .setId(1L).setName("User 1").setEmail("coach@vertice.com").setRole(Role.TRAINER).build());
        assertThat(page.getNextPageToken()).isEmpty();
    }

    @Test
    void listUsers_withMoreRowsThanThePage_returnsThePageAndATokenForTheRest() {
        when(userRepository.findByIdGreaterThanOrderByIdAsc(5L, Limit.of(3))).thenReturn(List.of(
                user(6L, com.vertice.api.user.Role.CLIENT, "a@vertice.com", VALID_CPF),
                user(8L, com.vertice.api.user.Role.CLIENT, "b@vertice.com", OTHER_VALID_CPF),
                user(9L, com.vertice.api.user.Role.CLIENT, "c@vertice.com", VALID_CPF)));

        var page = service.listUsers(null, 2, 5L);

        assertThat(page.getUsersList()).extracting(UserResponse::getId).containsExactly(6L, 8L);
        assertThat(page.getUsersList()).extracting(UserResponse::getCpf).containsOnly("");
        assertThat(page.getNextPageToken()).isEqualTo("8");
    }

    private static User user(long id, com.vertice.api.user.Role role, String email, String cpf) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setEmail(email);
        user.setCpf(cpf);
        user.setRole(role);
        return user;
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
        verify(tokenRepository).deleteUnusedByUserId(1L);
    }

    @Test
    void setPassword_throwsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPassword(99L, "brandNewPassword1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createUser_normalizesEmailBeforeCheckingAndSaving() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createUser(UserCreateRequest.newBuilder()
                .setName("New Coach").setEmail("  Coach@Vertice.COM ").setPassword("supersecret1")
                .setCpf(VALID_CPF).setRole(Role.TRAINER).build());

        verify(userRepository).findByEmail("coach@vertice.com");
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("coach@vertice.com");
    }

    @Test
    void createUser_withMixedCaseEmailOfExistingUser_throwsDuplicateEmail() {
        User existing = new User();
        existing.setId(1L);
        when(userRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createUser(UserCreateRequest.newBuilder()
                .setName("New Coach").setEmail("COACH@vertice.com").setPassword("supersecret1")
                .setCpf(VALID_CPF).setRole(Role.TRAINER).build()))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_withoutPassword_savesNullHash() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createUser(UserCreateRequest.newBuilder()
                .setName("New Client").setEmail("client@vertice.com")
                .setCpf(VALID_CPF).setRole(Role.CLIENT).build());

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNull();
    }

    @Test
    void updateUser_normalizesEmail() {
        User existing = new User();
        existing.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateUser(1L, UserRequest.newBuilder()
                .setName("Coach").setEmail(" Coach@Vertice.com").setCpf(VALID_CPF).setRole(Role.TRAINER).build());

        verify(userRepository).findByEmail("coach@vertice.com");
        assertThat(existing.getEmail()).isEqualTo("coach@vertice.com");
    }

    @Test
    void listUsersWithoutPassword_filtersByRoleWhenGiven() {
        User client = userWithPassword(7L, null);
        client.setRole(com.vertice.api.user.Role.CLIENT);
        when(userRepository.findByRoleAndPasswordHashIsNull(com.vertice.api.user.Role.CLIENT)).thenReturn(List.of(client));

        assertThat(service.listUsersWithoutPassword(com.vertice.api.user.Role.CLIENT))
                .containsExactly(UserResponse.newBuilder().setId(7L).setName(client.getName()).setRole(Role.CLIENT).build());
    }

    @Test
    void listUsersWithoutPassword_withoutRole_listsAll() {
        when(userRepository.findByPasswordHashIsNull()).thenReturn(List.of());

        assertThat(service.listUsersWithoutPassword(null)).isEmpty();
        verify(userRepository).findByPasswordHashIsNull();
    }

    @Test
    void verifyCredentials_withRightPassword_returnsUser() {
        User user = userWithPassword(1L, passwordEncoder.encode("supersecret1"));
        when(userRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(user));

        UserResponse response = service.verifyCredentials("coach@vertice.com", "supersecret1");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("coach@vertice.com");
    }

    @Test
    void verifyCredentials_matchesEmailCaseInsensitively() {
        User user = userWithPassword(1L, passwordEncoder.encode("supersecret1"));
        when(userRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(user));

        assertThat(service.verifyCredentials("  Coach@VERTICE.com ", "supersecret1").getId()).isEqualTo(1L);
    }

    @Test
    void verifyCredentials_withWrongPassword_throwsInvalidCredentials() {
        User user = userWithPassword(1L, passwordEncoder.encode("supersecret1"));
        when(userRepository.findByEmail("coach@vertice.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyCredentials("coach@vertice.com", "wrongpassword"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void verifyCredentials_withUnknownEmail_throwsSameErrorAfterADummyCompare() {
        PasswordEncoder encoder = spy(new BCryptPasswordEncoder());
        UserService spiedService = new UserService(userRepository, Mappers.getMapper(UserMapper.class), encoder, tokenRepository);
        when(userRepository.findByEmail("nobody@vertice.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spiedService.verifyCredentials("nobody@vertice.com", "supersecret1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(encoder).matches(eq("supersecret1"), startsWith("$2"));
    }

    @Test
    void verifyCredentials_withNoPasswordSet_throwsSameErrorAfterADummyCompare() {
        PasswordEncoder encoder = spy(new BCryptPasswordEncoder());
        UserService spiedService = new UserService(userRepository, Mappers.getMapper(UserMapper.class), encoder, tokenRepository);
        when(userRepository.findByEmail("client@vertice.com")).thenReturn(Optional.of(userWithPassword(2L, null)));

        assertThatThrownBy(() -> spiedService.verifyCredentials("client@vertice.com", "supersecret1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(encoder).matches(eq("supersecret1"), startsWith("$2"));
    }

    @Test
    void verifyCredentials_withLegacyEmptyHash_throwsInvalidCredentials() {
        when(userRepository.findByEmail("client@vertice.com")).thenReturn(Optional.of(userWithPassword(2L, "")));

        assertThatThrownBy(() -> service.verifyCredentials("client@vertice.com", ""))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static User userWithPassword(Long id, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setName("Someone");
        user.setEmail("coach@vertice.com");
        user.setCpf(VALID_CPF);
        user.setRole(com.vertice.api.user.Role.TRAINER);
        user.setPasswordHash(passwordHash);
        return user;
    }
}
