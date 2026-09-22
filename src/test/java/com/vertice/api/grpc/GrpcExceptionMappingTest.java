package com.vertice.api.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ExerciseInUseException;
import com.vertice.api.common.exception.PermissionDeniedException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.exception.UnauthenticatedException;
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

/**
 * Exercises {@link GrpcExceptionAdvice} and {@link GrpcRequestValidator} end-to-end through a
 * real RPC call — not just direct method invocation — to prove the {@code @GrpcAdvice} dispatch
 * mechanism itself works, the same standard {@code TrainerControllerTest} holds REST's
 * {@code GlobalExceptionHandler} to. No {@code .proto} file needed: the test-only service is
 * built directly from the {@code google.protobuf.Empty}/{@code StringValue} well-known types,
 * which ship inside {@code protobuf-java} with no codegen required.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19092", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
@Import(GrpcExceptionMappingTest.FailingServiceConfig.class)
class GrpcExceptionMappingTest {

    private static final String SERVICE_NAME = "com.vertice.api.grpc.test.Failing";

    private static final MethodDescriptor<StringValue, Empty> TRIGGER_METHOD =
            MethodDescriptor.<StringValue, Empty>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Trigger"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
                    .build();

    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void resourceNotFoundException_mapsToNotFound() {
        assertThatThrownBy(() -> trigger("not-found"))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void duplicateEmailException_mapsToAlreadyExists() {
        assertThatThrownBy(() -> trigger("duplicate"))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void constraintViolationException_mapsToInvalidArgumentWithFieldDetail() {
        assertThatThrownBy(() -> trigger("validation"))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(ex.getStatus().getDescription()).contains("name");
                });
    }

    @Test
    void constraintViolationWithoutViolations_keepsMessageAsDescription() {
        assertThatThrownBy(() -> trigger("validation-message"))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("muscleGroupIds: unknown muscle group 99");
                });
    }

    @Test
    void exerciseInUseException_mapsToFailedPrecondition() {
        assertThatThrownBy(() -> trigger("in-use"))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Exercise 3 is used by a workout and cannot be deleted");
                });
    }

    @Test
    void unauthenticatedException_mapsToUnauthenticated() {
        assertThatThrownBy(() -> trigger("unauthenticated"))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("Caller identity required");
                });
    }

    @Test
    void permissionDeniedException_mapsToPermissionDenied() {
        assertThatThrownBy(() -> trigger("permission-denied"))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
                    assertThat(ex.getStatus().getDescription()).isEqualTo("You do not have access to exercise 7");
                });
    }

    private Empty trigger(String selector) {
        channel = NettyChannelBuilder.forTarget("localhost:19092").usePlaintext().build();
        return ClientCalls.blockingUnaryCall(channel, TRIGGER_METHOD, CallOptions.DEFAULT,
                StringValue.of(selector));
    }

    @TestConfiguration
    static class FailingServiceConfig {

        @Bean
        BindableService failingService(GrpcRequestValidator validator) {
            ServerCalls.UnaryMethod<StringValue, Empty> handler = (request, responseObserver) -> {
                switch (request.getValue()) {
                    case "not-found" -> throw new ResourceNotFoundException("Trainer", 1L);
                    case "duplicate" -> throw new DuplicateEmailException("a@b.com");
                    case "validation" -> validator.validate(new ValidationProbe(""));
                    case "validation-message" ->
                            throw new ConstraintViolationException("muscleGroupIds: unknown muscle group 99", Set.of());
                    case "in-use" -> throw new ExerciseInUseException(3L);
                    case "unauthenticated" -> throw new UnauthenticatedException();
                    case "permission-denied" -> throw PermissionDeniedException.noAccess("exercise", 7L);
                    default -> { }
                }
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            };

            return () -> ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(TRIGGER_METHOD, ServerCalls.asyncUnaryCall(handler))
                    .build();
        }

        private record ValidationProbe(@NotBlank String name) {
        }
    }
}
