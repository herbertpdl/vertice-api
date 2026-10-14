package com.vertice.api.plan;

import com.vertice.api.grpc.ServiceSubject;
import com.vertice.api.grpc.TestJwts;
import com.vertice.api.user.Role;
import com.vertice.api.user.UserRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.Mockito.when;

/**
 * The callers the plan-tree controller tests try every RPC with: a plan owned by {@link #TRAINER_ID}
 * and written for {@link #CLIENT_ID} ({@link #PARTIES}), another trainer and another client, ADMIN,
 * the BFF's service identity and no token at all. Every user caller has to exist with its token's
 * role ({@code CallerIdentityResolver} checks), so tests that mock {@link UserRepository} call
 * {@link #storeUsers}.
 */
public record PlanCallers<S extends AbstractStub<S>>(
        S anonymous, S admin, S trainer, S otherTrainer, S client, S otherClient, S bff) {

    public static final long ADMIN_ID = 1L;
    public static final long TRAINER_ID = 10L;
    public static final long OTHER_TRAINER_ID = 11L;
    public static final long CLIENT_ID = 20L;
    public static final long OTHER_CLIENT_ID = 21L;
    public static final PlanParties PARTIES = new PlanParties(TRAINER_ID, CLIENT_ID);

    public static <S extends AbstractStub<S>> PlanCallers<S> of(S anonymous) {
        return new PlanCallers<>(anonymous,
                TestJwts.asCaller(anonymous, ADMIN_ID, Role.ADMIN),
                TestJwts.asCaller(anonymous, TRAINER_ID, Role.TRAINER),
                TestJwts.asCaller(anonymous, OTHER_TRAINER_ID, Role.TRAINER),
                TestJwts.asCaller(anonymous, CLIENT_ID, Role.CLIENT),
                TestJwts.asCaller(anonymous, OTHER_CLIENT_ID, Role.CLIENT),
                TestJwts.asService(anonymous, ServiceSubject.BFF));
    }

    /** Stubs a mocked {@link UserRepository} so every user caller above exists with its token's role. */
    public static void storeUsers(UserRepository userRepository) {
        when(userRepository.findRoleById(ADMIN_ID)).thenReturn(Optional.of(Role.ADMIN));
        when(userRepository.findRoleById(TRAINER_ID)).thenReturn(Optional.of(Role.TRAINER));
        when(userRepository.findRoleById(OTHER_TRAINER_ID)).thenReturn(Optional.of(Role.TRAINER));
        when(userRepository.findRoleById(CLIENT_ID)).thenReturn(Optional.of(Role.CLIENT));
        when(userRepository.findRoleById(OTHER_CLIENT_ID)).thenReturn(Optional.of(Role.CLIENT));
    }

    /** Read rule: the plan's trainer, its client and ADMIN are allowed; another trainer or client is not. */
    public void assertReadRule(Consumer<S> call) {
        assertAllowed(() -> call.accept(trainer));
        assertAllowed(() -> call.accept(client));
        assertAllowed(() -> call.accept(admin));
        assertDenied(() -> call.accept(otherTrainer));
        assertDenied(() -> call.accept(otherClient));
    }

    /** Write rule: the plan's trainer and ADMIN are allowed; its client, another trainer or client are not. */
    public void assertWriteRule(Consumer<S> call) {
        assertAllowed(() -> call.accept(trainer));
        assertAllowed(() -> call.accept(admin));
        assertDenied(() -> call.accept(client));
        assertDenied(() -> call.accept(otherTrainer));
        assertDenied(() -> call.accept(otherClient));
    }

    /** Log-owner rule: the log's client and ADMIN are allowed; the plan's trainer, another trainer or client are not. */
    public void assertClientRule(Consumer<S> call) {
        assertAllowed(() -> call.accept(client));
        assertAllowed(() -> call.accept(admin));
        assertDenied(() -> call.accept(trainer));
        assertDenied(() -> call.accept(otherTrainer));
        assertDenied(() -> call.accept(otherClient));
    }

    /** A service token is {@code PERMISSION_DENIED}, no token {@code UNAUTHENTICATED}. */
    public void assertNoServiceOrAnonymous(Consumer<S> call) {
        assertDenied(() -> call.accept(bff));
        assertCode(() -> call.accept(anonymous), Status.Code.UNAUTHENTICATED);
    }

    public static void assertAllowed(ThrowingCallable call) {
        assertThatCode(call).doesNotThrowAnyException();
    }

    public static void assertDenied(ThrowingCallable call) {
        assertCode(call, Status.Code.PERMISSION_DENIED);
    }

    public static void assertCode(ThrowingCallable call, Status.Code code) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .extracting(ex -> ex.getStatus().getCode())
                .isEqualTo(code);
    }
}
