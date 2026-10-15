package com.vertice.api.trainerclient;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.EndTrainerClientRelationshipRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.plan.PlanCallers;
import com.vertice.api.user.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.vertice.api.grpc.GrpcAssertions.assertInvalidField;
import static com.vertice.api.plan.PlanCallers.assertAllowed;
import static com.vertice.api.plan.PlanCallers.assertDenied;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.grpc.server.port=19102", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class TrainerClientControllerTest {

    private static final String VALID_CPF = "11144477735";
    private static final long TRAINER_ID = PlanCallers.TRAINER_ID;
    private static final long CLIENT_ID = PlanCallers.CLIENT_ID;

    @MockitoBean
    private TrainerClientService trainerClientService;

    @MockitoBean
    private UserRepository userRepository;

    /** Read by the end-relationship rule: only {@link #TRAINER_ID}→{@link #CLIENT_ID} is active. */
    @MockitoBean
    private TrainerClientRepository trainerClientRepository;

    private ManagedChannel channel;
    /** Calls as trainer {@link #TRAINER_ID}, the trainer the requests below name. */
    private TrainerClientServiceGrpc.TrainerClientServiceBlockingStub stub;
    private PlanCallers<TrainerClientServiceGrpc.TrainerClientServiceBlockingStub> callers;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19102").usePlaintext().build();
        callers = PlanCallers.of(TrainerClientServiceGrpc.newBlockingStub(channel));
        PlanCallers.storeUsers(userRepository);
        when(trainerClientRepository.existsByTrainerIdAndClientIdAndEndedAtIsNull(TRAINER_ID, CLIENT_ID)).thenReturn(true);
        stub = callers.trainer();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static CreateClientForTrainerRequest.Builder validCreateRequest() {
        return CreateClientForTrainerRequest.newBuilder()
                .setTrainerId(TRAINER_ID).setName("New Client").setEmail("client@vertice.com")
                .setPassword("supersecret1").setCpf(VALID_CPF);
    }

    @Test
    void createClientForTrainer_withValidRequest_returnsCreated() {
        UserResponse created = UserResponse.newBuilder().setId(2L).setName("New Client")
                .setEmail("client@vertice.com").setCpf(VALID_CPF).setRole(Role.CLIENT).build();
        when(trainerClientService.createClientForTrainer(any())).thenReturn(created);

        UserResponse response = stub.createClientForTrainer(validCreateRequest().build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createClientForTrainer_withMissingTrainerId_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setTrainerId(0).build()));
    }

    @Test
    void createClientForTrainer_withBlankName_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setName("").build()));
    }

    @Test
    void createClientForTrainer_withMalformedEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setEmail("not-an-email").build()));
    }

    @Test
    void createClientForTrainer_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setPassword("short").build()));
    }

    @Test
    void createClientForTrainer_withoutPassword_isAccepted() {
        UserResponse created = UserResponse.newBuilder().setId(2L).setRole(Role.CLIENT).build();
        when(trainerClientService.createClientForTrainer(any())).thenReturn(created);

        assertThat(stub.createClientForTrainer(validCreateRequest().clearPassword().build())).isEqualTo(created);
    }

    @Test
    void createClientForTrainer_withPasswordOver72Chars_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setPassword("x".repeat(73)).build()));
    }

    @Test
    void createClientForTrainer_withBlankPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setPassword(" ".repeat(8)).build()));
        verifyNoInteractions(trainerClientService);
    }

    @Test
    void createClientForTrainer_withPasswordOver72Bytes_throwsInvalidArgument() {
        // 40 characters but 80 UTF-8 bytes: over bcrypt's 72-byte limit.
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setPassword("é".repeat(40)).build()));
        verifyNoInteractions(trainerClientService);
    }

    @Test
    void createClientForTrainer_withMixedCaseEmail_isAccepted() {
        UserResponse created = UserResponse.newBuilder().setId(2L).setRole(Role.CLIENT).build();
        when(trainerClientService.createClientForTrainer(any())).thenReturn(created);

        assertThat(stub.createClientForTrainer(validCreateRequest().setEmail(" Client@Vertice.com ").build())).isEqualTo(created);
    }

    /** P1-04: a client's CPF is optional (data minimisation). */
    @Test
    void createClientForTrainer_withoutCpf_isAccepted() {
        UserResponse created = UserResponse.newBuilder().setId(2L).setRole(Role.CLIENT).build();
        when(trainerClientService.createClientForTrainer(any())).thenReturn(created);

        assertThat(stub.createClientForTrainer(validCreateRequest().setCpf("").build())).isEqualTo(created);
    }

    @Test
    void createClientForTrainer_withInvalidCpf_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setCpf("00000000000").build()));
    }

    @Test
    void createClientForTrainer_adminNamingAMissingTrainer_throwsNotFound() {
        when(trainerClientService.createClientForTrainer(any())).thenThrow(new ResourceNotFoundException("Trainer", 99L));

        assertThatThrownBy(() -> callers.admin().createClientForTrainer(validCreateRequest().setTrainerId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void listClientsForTrainer_returnsClients() {
        UserResponse client = UserResponse.newBuilder().setId(2L).setName("Client")
                .setEmail("client@vertice.com").setCpf(VALID_CPF).setRole(Role.CLIENT).build();
        when(trainerClientService.listClientsForTrainer(TRAINER_ID))
                .thenReturn(ListClientsForTrainerResponse.newBuilder().addClients(client).build());

        var response = stub.listClientsForTrainer(ListClientsForTrainerRequest.newBuilder().setTrainerId(TRAINER_ID).build());

        assertThat(response.getClientsList()).containsExactly(client);
    }

    @Test
    void listClientsForTrainer_withNoClients_returnsEmpty() {
        when(trainerClientService.listClientsForTrainer(TRAINER_ID))
                .thenReturn(ListClientsForTrainerResponse.newBuilder().build());

        var response = stub.listClientsForTrainer(ListClientsForTrainerRequest.newBuilder().setTrainerId(TRAINER_ID).build());

        assertThat(response.getClientsList()).isEmpty();
    }

    @Test
    void isTrainersClient_whenActiveRelationshipExists_returnsTrue() {
        when(trainerClientService.isTrainersClient(eq(TRAINER_ID), eq(CLIENT_ID)))
                .thenReturn(IsTrainersClientResponse.newBuilder().setIsClient(true).build());

        var response = stub.isTrainersClient(IsTrainersClientRequest.newBuilder().setTrainerId(TRAINER_ID).setClientId(CLIENT_ID).build());

        assertThat(response.getIsClient()).isTrue();
    }

    @Test
    void isTrainersClient_whenNoRelationshipExists_returnsFalse() {
        when(trainerClientService.isTrainersClient(eq(TRAINER_ID), eq(3L)))
                .thenReturn(IsTrainersClientResponse.newBuilder().setIsClient(false).build());

        var response = stub.isTrainersClient(IsTrainersClientRequest.newBuilder().setTrainerId(TRAINER_ID).setClientId(3L).build());

        assertThat(response.getIsClient()).isFalse();
    }

    // --- Authorization (P1-01): a TRAINER names only themself, ADMIN anyone; CLIENTs, other
    // trainers and service identities get nothing, and the service is never reached.

    @Test
    void createClientForTrainer_authorization() {
        when(trainerClientService.createClientForTrainer(any())).thenReturn(UserResponse.newBuilder().setId(2L).build());
        CreateClientForTrainerRequest request = validCreateRequest().build();

        assertTrainerAsThemselfOrAdminRule(stub -> stub.createClientForTrainer(request));
    }

    @Test
    void listClientsForTrainer_authorization() {
        when(trainerClientService.listClientsForTrainer(TRAINER_ID)).thenReturn(ListClientsForTrainerResponse.getDefaultInstance());
        ListClientsForTrainerRequest request = ListClientsForTrainerRequest.newBuilder().setTrainerId(TRAINER_ID).build();

        assertTrainerAsThemselfOrAdminRule(stub -> stub.listClientsForTrainer(request));
    }

    @Test
    void isTrainersClient_authorization() {
        when(trainerClientService.isTrainersClient(TRAINER_ID, CLIENT_ID)).thenReturn(IsTrainersClientResponse.getDefaultInstance());
        IsTrainersClientRequest request = IsTrainersClientRequest.newBuilder().setTrainerId(TRAINER_ID).setClientId(CLIENT_ID).build();

        assertTrainerAsThemselfOrAdminRule(stub -> stub.isTrainersClient(request));
    }

    @Test
    void anotherTrainer_namingTheTrainer_neverReachesTheService() {
        var otherTrainer = callers.otherTrainer();

        assertDenied(() -> otherTrainer.createClientForTrainer(validCreateRequest().build()));
        assertDenied(() -> otherTrainer.listClientsForTrainer(ListClientsForTrainerRequest.newBuilder().setTrainerId(TRAINER_ID).build()));
        assertDenied(() -> otherTrainer.isTrainersClient(
                IsTrainersClientRequest.newBuilder().setTrainerId(TRAINER_ID).setClientId(CLIENT_ID).build()));
        assertDenied(() -> otherTrainer.endTrainerClientRelationship(endRequest(TRAINER_ID, CLIENT_ID)));
        verifyNoInteractions(trainerClientService);
    }

    @Test
    void endTrainerClientRelationship_endsTheTrainersActiveRelationship() {
        stub.endTrainerClientRelationship(endRequest(TRAINER_ID, CLIENT_ID));

        verify(trainerClientService).endRelationship(TRAINER_ID, CLIENT_ID);
    }

    @Test
    void endTrainerClientRelationship_authorization() {
        EndTrainerClientRelationshipRequest request = endRequest(TRAINER_ID, CLIENT_ID);

        assertTrainerAsThemselfOrAdminRule(stub -> stub.endTrainerClientRelationship(request));
    }

    /** Not on the trainer's active roster, whether the client exists or not: the same refusal, before the service. */
    @Test
    void endTrainerClientRelationship_forAClientNotOnTheRoster_isDenied() {
        assertDenied(() -> stub.endTrainerClientRelationship(endRequest(TRAINER_ID, PlanCallers.OTHER_CLIENT_ID)));
        assertDenied(() -> stub.endTrainerClientRelationship(endRequest(TRAINER_ID, 999L)));
        verifyNoInteractions(trainerClientService);
    }

    @Test
    void endTrainerClientRelationship_adminWithNoActiveRelationship_throwsNotFound() {
        doThrow(new ResourceNotFoundException("Active relationship for client", 999L))
                .when(trainerClientService).endRelationship(TRAINER_ID, 999L);

        assertThatThrownBy(() -> callers.admin().endTrainerClientRelationship(endRequest(TRAINER_ID, 999L)))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    private static EndTrainerClientRelationshipRequest endRequest(long trainerId, long clientId) {
        return EndTrainerClientRelationshipRequest.newBuilder().setTrainerId(trainerId).setClientId(clientId).build();
    }

    private void assertTrainerAsThemselfOrAdminRule(Consumer<TrainerClientServiceGrpc.TrainerClientServiceBlockingStub> call) {
        assertAllowed(() -> call.accept(callers.trainer()));
        assertAllowed(() -> call.accept(callers.admin()));
        assertDenied(() -> call.accept(callers.otherTrainer()));
        assertDenied(() -> call.accept(callers.client()));
        assertDenied(() -> call.accept(callers.otherClient()));
        callers.assertNoServiceOrAnonymous(call);
        assertDenied(() -> call.accept(TestJwts.asService(callers.anonymous(), ServiceSubject.OPS)));
    }

    private void assertInvalidArgument(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // Upper bounds (P1-09): users.name and email are VARCHAR(255). Both addresses are valid for
    // @Email (64-character local part, labels of at most 63), so only the length decides.
    private static final String EMAIL_255 = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".com";
    private static final String EMAIL_256 = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(59) + ".com";

    @Test
    void createClientForTrainer_withNameAndEmailAtTheBound_isAccepted() {
        when(trainerClientService.createClientForTrainer(any())).thenReturn(UserResponse.getDefaultInstance());

        stub.createClientForTrainer(validCreateRequest().setName("n".repeat(255)).setEmail(EMAIL_255).build());
    }

    @Test
    void createClientForTrainer_pastTheBound_throwsInvalidArgument() {
        assertInvalidField(() -> stub.createClientForTrainer(validCreateRequest().setName("n".repeat(256)).build()), "name");
        assertInvalidField(() -> stub.createClientForTrainer(validCreateRequest().setEmail(EMAIL_256).build()), "email");
    }
}
