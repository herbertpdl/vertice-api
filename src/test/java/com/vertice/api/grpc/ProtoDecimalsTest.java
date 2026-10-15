package com.vertice.api.grpc;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtoDecimalsTest {

    @Test
    void blankIsUnset() {
        assertThat(ProtoDecimals.stringToWeight("")).isNull();
        assertThat(ProtoDecimals.stringToWeight("  ")).isNull();
        assertThat(ProtoDecimals.stringToLoadPercentage(null)).isNull();
    }

    @Test
    void weight_fitsNumericSixTwo() {
        assertThat(ProtoDecimals.stringToWeight("9999.99")).isEqualByComparingTo("9999.99");
        assertThat(ProtoDecimals.stringToWeight("0")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ProtoDecimals.stringToWeight("0.05")).isEqualByComparingTo("0.05");
        // Trailing zeros past the second decimal place are not precision.
        assertThat(ProtoDecimals.stringToWeight("80.500")).isEqualByComparingTo("80.5");
    }

    @Test
    void weight_pastNumericSixTwo_isRefused() {
        assertRefused(() -> ProtoDecimals.stringToWeight("10000"), "weight: must have at most 4 digits before and 2 after the decimal point");
        assertRefused(() -> ProtoDecimals.stringToWeight("10.005"), "weight: must have at most 4 digits before and 2 after the decimal point");
        assertRefused(() -> ProtoDecimals.stringToWeight("1E+4"), "weight: must have at most 4 digits before and 2 after the decimal point");
        // An exponent this large overflows an int digit count; the check must still refuse it.
        assertRefused(() -> ProtoDecimals.stringToWeight("1E2147483647"), "weight: must have at most 4 digits before and 2 after the decimal point");
    }

    @Test
    void loadPercentage_fitsNumericFiveTwo() {
        assertThat(ProtoDecimals.stringToLoadPercentage("999.99")).isEqualByComparingTo("999.99");
        assertRefused(() -> ProtoDecimals.stringToLoadPercentage("1000"),
                "loadPercentage: must have at most 3 digits before and 2 after the decimal point");
    }

    @Test
    void malformedOrNegative_isRefused_namingTheField() {
        assertRefused(() -> ProtoDecimals.stringToWeight("ten"), "weight: must be a valid decimal number");
        assertRefused(() -> ProtoDecimals.stringToLoadPercentage("-1"), "loadPercentage: must not be negative");
    }

    @Test
    void decimalToString_isPlain() {
        assertThat(ProtoDecimals.decimalToString(new BigDecimal("1E+2"))).isEqualTo("100");
        assertThat(ProtoDecimals.decimalToString(null)).isEmpty();
    }

    private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOf(ConstraintViolationException.class).hasMessage(message);
    }
}
