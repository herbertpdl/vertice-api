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
 *
 * <p>Each field is checked against its column's precision, so a value Postgres would refuse
 * ({@code numeric field overflow}) or silently round is {@code INVALID_ARGUMENT} here instead.
 * Trailing zeros don't count ({@code "80.500"} is {@code 80.5}).
 */
public final class ProtoDecimals {

    /** {@code NUMERIC(6,2)}: {@code exercise_sets.weight}, {@code set_logs.weight}; at most 9999.99. */
    public static final int WEIGHT_INTEGER_DIGITS = 4;
    /** {@code NUMERIC(5,2)}: {@code exercise_sets.load_percentage}; at most 999.99. */
    public static final int LOAD_PERCENTAGE_INTEGER_DIGITS = 3;
    public static final int FRACTION_DIGITS = 2;

    private ProtoDecimals() {
    }

    @Named("stringToWeight")
    public static BigDecimal stringToWeight(String value) {
        return stringToDecimal("weight", value, WEIGHT_INTEGER_DIGITS);
    }

    @Named("stringToLoadPercentage")
    public static BigDecimal stringToLoadPercentage(String value) {
        return stringToDecimal("loadPercentage", value, LOAD_PERCENTAGE_INTEGER_DIGITS);
    }

    @Named("decimalToString")
    public static String decimalToString(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static BigDecimal stringToDecimal(String field, String value, int integerDigits) {
        if (value == null || value.isBlank()) {
            return null;
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw invalid(field, "must be a valid decimal number");
        }
        if (parsed.signum() < 0) {
            throw invalid(field, "must not be negative");
        }
        BigDecimal stripped = parsed.stripTrailingZeros();
        // long: an exponent like 1E2147483647 would overflow int arithmetic into a negative count.
        if (stripped.scale() > FRACTION_DIGITS || (long) stripped.precision() - stripped.scale() > integerDigits) {
            throw invalid(field, "must have at most %d digits before and %d after the decimal point"
                    .formatted(integerDigits, FRACTION_DIGITS));
        }
        return parsed;
    }

    private static ConstraintViolationException invalid(String field, String message) {
        return new ConstraintViolationException(field + ": " + message, Set.of());
    }
}
