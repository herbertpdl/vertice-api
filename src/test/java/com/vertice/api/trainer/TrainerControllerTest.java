package com.vertice.api.trainer;

import com.google.protobuf.Empty;
import com.vertice.api.common.exception.DuplicateCpfException;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.generated.grpc.trainer.v1.DeleteTrainerRequest;
import com.vertice.api.generated.grpc.trainer.v1.GetTrainerRequest;
import com.vertice.api.generated.grpc.trainer.v1.ListTrainersRequest;
import com.vertice.api.generated.grpc.trainer.v1.ListTrainersResponse;
import com.vertice.api.generated.grpc.trainer.v1.SetTrainerPasswordRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerCreateRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerRequest;
import com.vertice.api.generated.grpc.trainer.v1.TrainerResponse;
import com.vertice.api.generated.grpc.trainer.v1.TrainerServiceGrpc;
import com.vertice.api.generated.grpc.trainer.v1.UpdateTrainerRequest;
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

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.grpc.server.port=19093")
@ActiveProfiles("local")
class TrainerControllerTest {

    private static final String VALID_CPF = "11144477735";

    @MockitoBean
    private TrainerService trainerService;

    private ManagedChannel channel;
    private TrainerServiceGrpc.TrainerServiceBlockingStub stub;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19093").usePlaintext().build();
        stub = TrainerServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void listTrainers_returnsAll() {
        TrainerResponse trainer = TrainerResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).build();
        when(trainerService.listTrainers()).thenReturn(java.util.List.of(trainer));

        ListTrainersResponse response = stub.listTrainers(ListTrainersRequest.newBuilder().build());

        assertThat(response.getTrainersList()).containsExactly(trainer);
    }

    @Test
    void getTrainer_whenExists_returnsTrainer() {
        TrainerResponse trainer = TrainerResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).build();
        when(trainerService.getTrainer(1L)).thenReturn(trainer);

        TrainerResponse response = stub.getTrainer(GetTrainerRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(trainer);
    }

    @Test
    void getTrainer_whenMissing_throwsNotFound() {
        when(trainerService.getTrainer(99L)).thenThrow(new ResourceNotFoundException("Trainer", 99L));

        assertThatThrownBy(() -> stub.getTrainer(GetTrainerRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void createTrainer_withValidRequest_returnsCreated() {
        TrainerResponse created = TrainerResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).build();
        when(trainerService.createTrainer(any())).thenReturn(created);

        TrainerResponse response = stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build());

        assertThat(response).isEqualTo(created);
    }

    @Test
    void createTrainer_withBlankName_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build()));
    }

    @Test
    void createTrainer_withMalformedEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("not-an-email").setPassword("supersecret1").setCpf(VALID_CPF).build()));
    }

    @Test
    void createTrainer_withBlankEmail_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("").setPassword("supersecret1").setCpf(VALID_CPF).build()));
    }

    @Test
    void createTrainer_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("short").setCpf(VALID_CPF).build()));
    }

    @Test
    void createTrainer_withInvalidCpf_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf("00000000000").build()));
    }

    @Test
    void createTrainer_withDuplicateEmail_throwsAlreadyExists() {
        when(trainerService.createTrainer(any())).thenThrow(new DuplicateEmailException("coach@vertice.com"));

        assertThatThrownBy(() -> stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void createTrainer_withCref_returnsCreated() {
        TrainerResponse created = TrainerResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setCref("123456-G/SP").build();
        when(trainerService.createTrainer(any())).thenReturn(created);

        TrainerResponse response = stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).setCref("123456-G/SP").build());

        assertThat(response.getCref()).isEqualTo("123456-G/SP");
    }

    @Test
    void createTrainer_withoutCref_returnsCreated() {
        TrainerResponse created = TrainerResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).build();
        when(trainerService.createTrainer(any())).thenReturn(created);

        TrainerResponse response = stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build());

        assertThat(response.getCref()).isEmpty();
    }

    @Test
    void createTrainer_withDuplicateCpf_throwsAlreadyExists() {
        when(trainerService.createTrainer(any())).thenThrow(new DuplicateCpfException(VALID_CPF));

        assertThatThrownBy(() -> stub.createTrainer(TrainerCreateRequest.newBuilder()
                .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf(VALID_CPF).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void updateTrainer_whenExists_returnsUpdated() {
        TrainerResponse updated = TrainerResponse.newBuilder().setId(1L).setName("New Name").setEmail("coach@vertice.com").setCpf(VALID_CPF).build();
        when(trainerService.updateTrainer(eq(1L), any())).thenReturn(updated);

        TrainerResponse response = stub.updateTrainer(UpdateTrainerRequest.newBuilder()
                .setId(1L)
                .setTrainer(TrainerRequest.newBuilder().setName("New Name").setEmail("coach@vertice.com").setCpf(VALID_CPF).build())
                .build());

        assertThat(response.getName()).isEqualTo("New Name");
    }

    @Test
    void updateTrainer_withCref_returnsUpdated() {
        TrainerResponse updated = TrainerResponse.newBuilder().setId(1L).setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setCref("123456-G/SP").build();
        when(trainerService.updateTrainer(eq(1L), any())).thenReturn(updated);

        TrainerResponse response = stub.updateTrainer(UpdateTrainerRequest.newBuilder()
                .setId(1L)
                .setTrainer(TrainerRequest.newBuilder().setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).setCref("123456-G/SP").build())
                .build());

        assertThat(response.getCref()).isEqualTo("123456-G/SP");
    }

    @Test
    void updateTrainer_whenMissing_throwsNotFound() {
        when(trainerService.updateTrainer(eq(99L), any())).thenThrow(new ResourceNotFoundException("Trainer", 99L));

        assertThatThrownBy(() -> stub.updateTrainer(UpdateTrainerRequest.newBuilder()
                .setId(99L)
                .setTrainer(TrainerRequest.newBuilder().setName("Coach").setEmail("coach@vertice.com").setCpf(VALID_CPF).build())
                .build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void updateTrainer_withBlankName_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.updateTrainer(UpdateTrainerRequest.newBuilder()
                .setId(1L)
                .setTrainer(TrainerRequest.newBuilder().setName("").setEmail("coach@vertice.com").setCpf(VALID_CPF).build())
                .build()));
    }

    @Test
    void updateTrainer_withInvalidCpf_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.updateTrainer(UpdateTrainerRequest.newBuilder()
                .setId(1L)
                .setTrainer(TrainerRequest.newBuilder().setName("Coach").setEmail("coach@vertice.com").setCpf("not-a-cpf").build())
                .build()));
    }

    @Test
    void deleteTrainer_whenExists_succeeds() {
        Empty response = stub.deleteTrainer(DeleteTrainerRequest.newBuilder().setId(1L).build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void deleteTrainer_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("Trainer", 99L)).when(trainerService).deleteTrainer(99L);

        assertThatThrownBy(() -> stub.deleteTrainer(DeleteTrainerRequest.newBuilder().setId(99L).build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setTrainerPassword_whenExists_succeeds() {
        Empty response = stub.setTrainerPassword(SetTrainerPasswordRequest.newBuilder()
                .setId(1L).setPassword("brandNewPassword1").build());

        assertThat(response).isEqualTo(Empty.getDefaultInstance());
    }

    @Test
    void setTrainerPassword_whenMissing_throwsNotFound() {
        doThrow(new ResourceNotFoundException("Trainer", 99L))
                .when(trainerService).setPassword(99L, "brandNewPassword1");

        assertThatThrownBy(() -> stub.setTrainerPassword(SetTrainerPasswordRequest.newBuilder()
                .setId(99L).setPassword("brandNewPassword1").build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void setTrainerPassword_withShortPassword_throwsInvalidArgument() {
        assertInvalidArgument(() -> stub.setTrainerPassword(SetTrainerPasswordRequest.newBuilder()
                .setId(1L).setPassword("short").build()));
    }

    private void assertInvalidArgument(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
