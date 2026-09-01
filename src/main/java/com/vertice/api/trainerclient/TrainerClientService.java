package com.vertice.api.trainerclient;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerResponse;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.user.User;
import com.vertice.api.user.UserMapper;
import com.vertice.api.user.UserRepository;
import com.vertice.api.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainerClientService {

    private final TrainerClientRepository trainerClientRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * One transaction spans both the new user's creation (delegated to {@link UserService}, which
     * already owns email/cpf-uniqueness and cref-only-for-trainer validation) and the
     * trainer/client relationship row — if the relationship insert fails, the user creation rolls
     * back with it rather than leaving an orphaned user with no trainer.
     */
    public UserResponse createClientForTrainer(CreateClientForTrainerRequest request) {
        User trainer = findUserWithRoleOrThrow(request.getTrainerId(), com.vertice.api.user.Role.TRAINER, "Trainer");

        UserCreateRequest createRequest = UserCreateRequest.newBuilder()
                .setName(request.getName())
                .setEmail(request.getEmail())
                .setPassword(request.getPassword())
                .setCpf(request.getCpf())
                .setCref("")
                .setRole(Role.CLIENT)
                .build();
        UserResponse createdClient = userService.createUser(createRequest);

        User client = userRepository.findById(createdClient.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Client", createdClient.getId()));

        TrainerClient trainerClient = new TrainerClient();
        trainerClient.setTrainer(trainer);
        trainerClient.setClient(client);
        trainerClient.setStartedAt(Instant.now());
        trainerClientRepository.save(trainerClient);

        return createdClient;
    }

    @Transactional(readOnly = true)
    public ListClientsForTrainerResponse listClientsForTrainer(Long trainerId) {
        var clients = trainerClientRepository.findByTrainerIdAndEndedAtIsNull(trainerId).stream()
                .map(TrainerClient::getClient)
                .map(userMapper::toResponse)
                .toList();
        return ListClientsForTrainerResponse.newBuilder().addAllClients(clients).build();
    }

    @Transactional(readOnly = true)
    public IsTrainersClientResponse isTrainersClient(Long trainerId, Long clientId) {
        boolean isClient = trainerClientRepository.existsByTrainerIdAndClientIdAndEndedAtIsNull(trainerId, clientId);
        return IsTrainersClientResponse.newBuilder().setIsClient(isClient).build();
    }

    private User findUserWithRoleOrThrow(Long id, com.vertice.api.user.Role role, String resourceName) {
        return userRepository.findById(id)
                .filter(user -> user.getRole() == role)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
    }
}
