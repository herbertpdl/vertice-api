package com.vertice.api.grpc;

import jakarta.validation.ConstraintViolationException;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Dates cross the wire as plain ISO-8601 ({@code yyyy-MM-dd}) strings, same "canonical form over
 * the wire" choice already made for other fields (see {@code cpf-field/spec.md} §0) rather than
 * introducing a dedicated proto date/timestamp type. Mirrors {@link ProtoStrings}.
 */
public final class ProtoDates {

    private ProtoDates() {
    }

    @Named("stringToDate")
    public static LocalDate stringToDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ConstraintViolationException("must be a valid ISO-8601 date (yyyy-MM-dd)", Set.of());
        }
    }

    @Named("dateToString")
    public static String dateToString(LocalDate value) {
        return value == null ? "" : value.toString();
    }
}
