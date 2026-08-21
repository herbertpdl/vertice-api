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
            "",
    })
    void invalidCpf_fails(String cpf) {
        assertThat(validator.validate(new Probe(cpf))).isNotEmpty();
    }

    @Test
    void nullCpf_fails() {
        assertThat(validator.validate(new Probe(null))).isNotEmpty();
    }
}
