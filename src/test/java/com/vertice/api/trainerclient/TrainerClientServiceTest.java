package com.vertice.api.trainerclient;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.user.User;
import com.vertice.api.user.UserMapper;
import com.vertice.api.user.UserRepository;
import com.vertice.api.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainerClientServiceTest {

    private static final String VALID_CPF = "11144477735";

    @Mock
    private TrainerClientRepository trainerClientRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    private TrainerClientService service;

    @BeforeEach
    void setUp() {
        service = new TrainerClientService(trainerClientRepository, userService, userRepository, Mappers.getMapper(UserMapper.class));
    }

    private static User user(long id, com.vertice.api.user.Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private static CreateClientForTrainerRequest.Builder validCreateRequest() {
        return CreateClientForTrainerRequest.newBuilder()
                .setTrainerId(1L)
                .setName("New Client")
                .setEmail("client@vertice.com")
                .setPassword("supersecret1")
                .setCpf(VALID_CPF);
    }

    @Test
    void createClientForTrainer_createsUserAndPersistsRelationship() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, com.vertice.api.user.Role.TRAINER)));
        UserResponse createdClient = UserResponse.newBuilder().setId(2L).setName("New Client")
                .setEmail("client@vertice.com").setCpf(VALID_CPF).setRole(Role.CLIENT).build();
        when(userService.createUser(any(UserCreateRequest.class))).thenReturn(createdClient);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, com.vertice.api.user.Role.CLIENT)));
        when(trainerClientRepository.save(any(TrainerClient.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createClientForTrainer(validCreateRequest().build());

        assertThat(response).isEqualTo(createdClient);

        var createRequestCaptor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).createUser(createRequestCaptor.capture());
        UserCreateRequest sentToUserService = createRequestCaptor.getValue();
        assertThat(sentToUserService.getName()).isEqualTo("New Client");
        assertThat(sentToUserService.getEmail()).isEqualTo("client@vertice.com");
        assertThat(sentToUserService.getCpf()).isEqualTo(VALID_CPF);
        assertThat(sentToUserService.getRole()).isEqualTo(Role.CLIENT);
        assertThat(sentToUserService.getCref()).isEmpty();

        var trainerClientCaptor = ArgumentCaptor.forClass(TrainerClient.class);
        verify(trainerClientRepository).save(trainerClientCaptor.capture());
        TrainerClient saved = trainerClientCaptor.getValue();
        assertThat(saved.getTrainer().getId()).isEqualTo(1L);
        assertThat(saved.getClient().getId()).isEqualTo(2L);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getEndedAt()).isNull();
    }

    @Test
    void createClientForTrainer_throwsWhenTrainerMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        var request = validCreateRequest().setTrainerId(99L).build();

        assertThatThrownBy(() -> service.createClientForTrainer(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userService, never()).createUser(any());
        verify(trainerClientRepository, never()).save(any());
    }

    @Test
    void createClientForTrainer_throwsWhenUserIsNotTrainer() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, com.vertice.api.user.Role.CLIENT)));

        var request = validCreateRequest().build();

        assertThatThrownBy(() -> service.createClientForTrainer(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userService, never()).createUser(any());
        verify(trainerClientRepository, never()).save(any());
    }

    @Test
    void listClientsForTrainer_returnsOnlyActiveClients() {
        TrainerClient relationship = new TrainerClient();
        relationship.setId(1L);
        relationship.setTrainer(user(1L, com.vertice.api.user.Role.TRAINER));
        User client = user(2L, com.vertice.api.user.Role.CLIENT);
        client.setName("Client");
        client.setEmail("client@vertice.com");
        client.setCpf(VALID_CPF);
        relationship.setClient(client);

        when(trainerClientRepository.findByTrainerIdAndEndedAtIsNull(1L)).thenReturn(java.util.List.of(relationship));

        var response = service.listClientsForTrainer(1L);

        assertThat(response.getClientsList()).hasSize(1);
        assertThat(response.getClientsList().getFirst().getId()).isEqualTo(2L);
    }

    @Test
    void listClientsForTrainer_returnsEmptyWhenNoActiveClients() {
        when(trainerClientRepository.findByTrainerIdAndEndedAtIsNull(1L)).thenReturn(java.util.List.of());

        var response = service.listClientsForTrainer(1L);

        assertThat(response.getClientsList()).isEmpty();
    }

    @Test
    void isTrainersClient_returnsTrueWhenActiveRelationshipExists() {
        when(trainerClientRepository.existsByTrainerIdAndClientIdAndEndedAtIsNull(1L, 2L)).thenReturn(true);

        var response = service.isTrainersClient(1L, 2L);

        assertThat(response.getIsClient()).isTrue();
    }

    @Test
    void isTrainersClient_returnsFalseWhenNoRelationshipExists() {
        when(trainerClientRepository.existsByTrainerIdAndClientIdAndEndedAtIsNull(1L, 2L)).thenReturn(false);

        var response = service.isTrainersClient(1L, 2L);

        assertThat(response.getIsClient()).isFalse();
    }
}
