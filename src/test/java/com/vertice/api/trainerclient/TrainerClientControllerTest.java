package com.vertice.api.trainerclient;

import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.trainerclient.v1.CreateClientForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.IsTrainersClientResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerRequest;
import com.vertice.api.generated.grpc.trainerclient.v1.ListClientsForTrainerResponse;
import com.vertice.api.generated.grpc.trainerclient.v1.TrainerClientServiceGrpc;
import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.grpc.server.port=19102", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class TrainerClientControllerTest {

    private static final String VALID_CPF = "11144477735";

    @MockitoBean
    private TrainerClientService trainerClientService;

    private ManagedChannel channel;
    private TrainerClientServiceGrpc.TrainerClientServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19102").usePlaintext().build();
        stub = TrainerClientServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static CreateClientForTrainerRequest.Builder validCreateRequest() {
        return CreateClientForTrainerRequest.newBuilder()
                .setTrainerId(1L).setName("New Client").setEmail("client@vertice.com")
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
    void createClientForTrainer_withInvalidCpf_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createClientForTrainer(validCreateRequest().setCpf("00000000000").build()));
    }

    @Test
    void createClientForTrainer_withMissingTrainer_throwsNotFound() {
        when(trainerClientService.createClientForTrainer(any())).thenThrow(new ResourceNotFoundException("Trainer", 99L));

        assertThatThrownBy(() -> stub.createClientForTrainer(validCreateRequest().setTrainerId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void listClientsForTrainer_returnsClients() {
        UserResponse client = UserResponse.newBuilder().setId(2L).setName("Client")
                .setEmail("client@vertice.com").setCpf(VALID_CPF).setRole(Role.CLIENT).build();
        when(trainerClientService.listClientsForTrainer(1L))
                .thenReturn(ListClientsForTrainerResponse.newBuilder().addClients(client).build());

        var response = stub.listClientsForTrainer(ListClientsForTrainerRequest.newBuilder().setTrainerId(1L).build());

        assertThat(response.getClientsList()).containsExactly(client);
    }

    @Test
    void listClientsForTrainer_withNoClients_returnsEmpty() {
        when(trainerClientService.listClientsForTrainer(1L))
                .thenReturn(ListClientsForTrainerResponse.newBuilder().build());

        var response = stub.listClientsForTrainer(ListClientsForTrainerRequest.newBuilder().setTrainerId(1L).build());

        assertThat(response.getClientsList()).isEmpty();
    }

    @Test
    void isTrainersClient_whenActiveRelationshipExists_returnsTrue() {
        when(trainerClientService.isTrainersClient(eq(1L), eq(2L)))
                .thenReturn(IsTrainersClientResponse.newBuilder().setIsClient(true).build());

        var response = stub.isTrainersClient(IsTrainersClientRequest.newBuilder().setTrainerId(1L).setClientId(2L).build());

        assertThat(response.getIsClient()).isTrue();
    }

    @Test
    void isTrainersClient_whenNoRelationshipExists_returnsFalse() {
        when(trainerClientService.isTrainersClient(eq(1L), eq(3L)))
                .thenReturn(IsTrainersClientResponse.newBuilder().setIsClient(false).build());

        var response = stub.isTrainersClient(IsTrainersClientRequest.newBuilder().setTrainerId(1L).setClientId(3L).build());

        assertThat(response.getIsClient()).isFalse();
    }

    private void assertInvalidArgument(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
