package com.vertice.api.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private record Probe(@Cpf String cpf) {
    }

    @Test
    void validCpf_passes() {
        assertThat(validator.validate(new Probe("11144477735"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00000000000", // all same digit
            "11111111111", // all same digit
            "12345678901", // wrong check digits
            "1114447773",  // too short
            "111444777355", // too long
            "1114447773a", // non-digit
            " ",           // blank is not "absent"
    })
    void invalidCpf_fails(String cpf) {
        assertThat(validator.validate(new Probe(cpf))).isNotEmpty();
    }

    // Absent is valid (P1-04): a CLIENT needs no CPF, and UserService requires one for a TRAINER.
    @Test
    void nullCpf_passes() {
        assertThat(validator.validate(new Probe(null))).isEmpty();
    }

    @Test
    void emptyCpf_passes() {
        assertThat(validator.validate(new Probe(""))).isEmpty();
    }
}
