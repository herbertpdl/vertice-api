package com.vertice.api.trainerclient;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.time.DbInstants;
import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.ClientTrainerResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerResponse;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.user.User;
import com.vertice.api.user.UserMapper;
import com.vertice.api.user.UserRepository;
import com.vertice.api.user.UserService;
import com.vertice.api.user.UserView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
        trainerClient.setStartedAt(DbInstants.now());
        trainerClientRepository.save(trainerClient);

        return UserView.DETAIL.restrict(createdClient);
    }

    /**
     * In the {@link UserView#SUMMARY} view: id, name and role only, no email, CPF or CREF (P1-03).
     * vertice-bff's roster needs nothing more.
     */
    @Transactional(readOnly = true)
    public ListClientsForTrainerResponse listClientsForTrainer(Long trainerId) {
        var clients = trainerClientRepository.findByTrainerIdAndEndedAtIsNull(trainerId).stream()
                .map(TrainerClient::getClient)
                .map(userMapper::toResponse)
                .map(UserView.SUMMARY::restrict)
                .toList();
        return ListClientsForTrainerResponse.newBuilder().addAllClients(clients).build();
    }

    /**
     * Sets {@code ended_at} on the active row, so {@code uq_trainer_clients_active_client} frees the
     * client for another trainer. Plans and logs keep pointing at both users: they are history,
     * not part of the relationship.
     */
    public void endRelationship(Long trainerId, Long clientId) {
        TrainerClient relationship = trainerClientRepository.findByTrainerIdAndClientIdAndEndedAtIsNull(trainerId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Active relationship for client", clientId));
        relationship.setEndedAt(DbInstants.now());
    }

    @Transactional(readOnly = true)
    public IsTrainersClientResponse isTrainersClient(Long trainerId, Long clientId) {
        boolean isClient = trainerClientRepository.existsByTrainerIdAndClientIdAndEndedAtIsNull(trainerId, clientId);
        return IsTrainersClientResponse.newBuilder().setIsClient(isClient).build();
    }

    /**
     * The client's active trainer with the contacts they chose to show (R74-R79): the name always,
     * e-mail, WhatsApp and Instagram only when switched on and filled in.
     */
    @Transactional(readOnly = true)
    public ClientTrainerResponse getClientTrainer(Long clientId) {
        User trainer = trainerClientRepository.findByClientIdAndEndedAtIsNull(clientId)
                .map(TrainerClient::getTrainer)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Active trainer of client", clientId));
        ClientTrainerResponse.Builder response = ClientTrainerResponse.newBuilder()
                .setTrainerId(trainer.getId())
                .setName(trainer.getName());
        if (trainer.isShowEmail() && trainer.getEmail() != null) {
            response.setEmail(trainer.getEmail());
        }
        if (trainer.isShowWhatsapp() && trainer.getWhatsapp() != null) {
            response.setWhatsapp(trainer.getWhatsapp());
        }
        if (trainer.isShowInstagram() && trainer.getInstagram() != null) {
            response.setInstagram(trainer.getInstagram());
        }
        return response.build();
    }

    /** A deleted account (P1-05) is missing: nobody, ADMIN included, links a client to it. */
    private User findUserWithRoleOrThrow(Long id, com.vertice.api.user.Role role, String resourceName) {
        return userRepository.findById(id)
                .filter(user -> !user.isDeleted())
                .filter(user -> user.getRole() == role)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
    }
}
