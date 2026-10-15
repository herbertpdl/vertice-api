package com.vertice.api.user;

import com.vertice.api.generated.grpc.user.v1.Role;
import com.vertice.api.generated.grpc.user.v1.UserCreateRequest;
import com.vertice.api.generated.grpc.user.v1.UserResponse;
import com.vertice.api.generated.grpc.user.v1.UserServiceGrpc;
import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code CreateUser} with {@code vertice.consent.terms-required-on-registration} on: the behaviour
 * the P1-04 follow-up makes permanent once vertice-bff sends {@code accepted_terms_version}.
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19117", "spring.datasource.hikari.maximum-pool-size=3",
        "vertice.consent.terms-required-on-registration=true"})
@ActiveProfiles("local")
class UserControllerTermsRequiredTest {

    private static final UserCreateRequest.Builder REGISTRATION = UserCreateRequest.newBuilder()
            .setName("Coach").setEmail("coach@vertice.com").setPassword("supersecret1").setCpf("11144477735").setRole(Role.TRAINER);

    @MockitoBean
    private UserService userService;

    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub bff;

    @BeforeEach
    void setUp() {
        channel = NettyChannelBuilder.forTarget("localhost:19117").usePlaintext().build();
        bff = TestJwts.asService(UserServiceGrpc.newBlockingStub(channel), ServiceSubject.BFF);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void createUser_withoutTermsVersion_throwsInvalidArgument() {
        assertThatThrownBy(() -> bff.createUser(REGISTRATION.clone().build()))
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(ex.getStatus().getDescription()).contains("accepted_terms_version");
                });
        verifyNoInteractions(userService);
    }

    @Test
    void createUser_withTermsVersion_isCreated() {
        when(userService.createUser(any())).thenReturn(UserResponse.newBuilder().setId(1L).setRole(Role.TRAINER).build());

        bff.createUser(REGISTRATION.clone().setAcceptedTermsVersion("2026-10-14").build());

        verify(userService).createUser(any());
    }
}
