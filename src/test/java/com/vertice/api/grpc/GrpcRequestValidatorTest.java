package com.vertice.api.grpc;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcRequestValidatorTest {

    private final GrpcRequestValidator validator =
            new GrpcRequestValidator(Validation.buildDefaultValidatorFactory().getValidator());

    private record Probe(@NotBlank String name) {
    }

    @Test
    void validate_withValidObject_doesNotThrow() {
        assertThatCode(() -> validator.validate(new Probe("Coach")))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_withInvalidObject_throwsConstraintViolationException() {
        assertThatThrownBy(() -> validator.validate(new Probe("")))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
