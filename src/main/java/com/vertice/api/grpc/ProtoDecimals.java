package com.vertice.api.grpc;

import jakarta.validation.ConstraintViolationException;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Decimal-precision fields (e.g. a set's weight) cross the wire as plain strings, not proto
 * {@code double} (float rounding risk on a value persisted as an exact {@link BigDecimal}) or
 * {@code int32} (loses the fractional part) — same "canonical form over the wire" choice
 * {@link ProtoDates} already made for {@code LocalDate}. Unlike {@code ProtoDates#stringToDate},
 * blank is a valid input here: it means "unset" (mirrors {@link ProtoStrings#nullToEmpty}'s
 * reverse direction), since these DB columns are nullable.
 */
public final class ProtoDecimals {

    private ProtoDecimals() {
    }

    @Named("stringToDecimal")
    public static BigDecimal stringToDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ConstraintViolationException("must be a valid decimal number", Set.of());
        }
        if (parsed.signum() < 0) {
            throw new ConstraintViolationException("must not be negative", Set.of());
        }
        return parsed;
    }

    @Named("decimalToString")
    public static String decimalToString(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
