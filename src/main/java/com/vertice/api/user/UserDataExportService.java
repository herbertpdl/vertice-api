package com.vertice.api.user;

import com.vertice.api.common.exception.ExportTooLargeException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.common.time.DbInstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;


/**
 * {@code ExportUserData} (P1-05, LGPD art. 18 II and V): everything vertice-api stores about the
 * caller as one JSON document ({@link UserDataExport}), per docs/data-inventory.md.
 *
 * <p>One repeatable-read, read-only transaction, so every section is read from the same snapshot:
 * a log recorded mid-export can't appear without its set logs. Bounded by
 * {@link UserDataExportProperties}: rows per section while reading, then the serialized size while
 * writing ({@link BoundedOutputStream}); past either limit
 * the export is refused ({@code RESOURCE_EXHAUSTED}) rather than truncated, since a partial export
 * would silently miss data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(UserDataExportProperties.class)
public class UserDataExportService {

    private final UserDataExportQueries queries;
    private final UserDataExportProperties properties;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String exportUserData(long userId) {
        UserDataExport.Profile profile = queries.profile(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        UserDataExport export = new UserDataExport(UserDataExport.FORMAT_VERSION, DbInstants.now(), profile,
                queries.consent(userId), queries.relationships(userId), queries.plans(userId),
                queries.workoutLogs(userId), queries.exercises(userId));

        // Written straight into a bounded buffer: an oversized document is refused while it is being
        // serialized, so memory never holds more than max-size of JSON (plus the serializer's buffer).
        BoundedOutputStream out = new BoundedOutputStream(properties.maxSize().toBytes());
        try {
            jsonMapper.writeValue(out, export);
        } catch (JacksonException ex) {
            // Jackson wraps what the stream throws with the property path; the limit is what matters.
            for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof ExportTooLargeException tooLarge) {
                    throw tooLarge;
                }
            }
            throw ex;
        }
        // Ids and size only: the line records that an export happened, not what it contained.
        log.info("User data exported: userId={} bytes={}", userId, out.size());
        return out.toUtf8String();
    }
}
