package com.vertice.api.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code vertice.export.*}: the bounds of one {@code ExportUserData} response (P1-05).
 *
 * @param maxRows rows loaded per section (plans, sets, logs, …) before the export is refused, so a
 *                pathological account can't load an unbounded result set into memory
 * @param maxSize the serialized document's limit; the default stays under gRPC's 4 MiB default
 *                receive limit, which vertice-bff's client keeps
 */
@ConfigurationProperties("vertice.export")
public record UserDataExportProperties(int maxRows, DataSize maxSize) {

    public UserDataExportProperties {
        if (maxRows <= 0) {
            throw new IllegalArgumentException("vertice.export.max-rows must be positive");
        }
        if (maxSize == null || maxSize.toBytes() <= 0) {
            throw new IllegalArgumentException("vertice.export.max-size must be positive");
        }
    }
}
