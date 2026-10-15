package com.vertice.api.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

public final class GrpcAssertions {

    private GrpcAssertions() {
    }

    /** The call is {@code INVALID_ARGUMENT} and the description names {@code field} first, as {@code GrpcExceptionAdvice} writes it. */
    public static void assertInvalidField(ThrowingCallable call, String field) {
        assertThatThrownBy(call)
                .asInstanceOf(throwable(StatusRuntimeException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(ex.getStatus().getDescription()).startsWith(field + ":");
                });
    }
}
