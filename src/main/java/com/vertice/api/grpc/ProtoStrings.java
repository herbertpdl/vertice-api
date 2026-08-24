package com.vertice.api.grpc;

import org.mapstruct.Named;

/**
 * Protobuf string setters reject {@code null} (unlike a plain JPA {@code String} column, which is
 * commonly nullable). Entity mappers map nullable String columns (e.g. a "description"/"notes"
 * field) to proto response fields through {@link #nullToEmpty}, so a {@code null} column value
 * becomes proto3's own empty-string zero value instead of throwing.
 */
public final class ProtoStrings {

    private ProtoStrings() {
    }

    @Named("nullToEmpty")
    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
