package com.vertice.api.user;

import com.vertice.api.common.exception.ExportTooLargeException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataExportServiceTest {

    @Mock
    private UserDataExportQueries queries;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void exportUserData_serializesEverySection() {
        stubAnAccount();
        UserDataExportService service = service(DataSize.ofMegabytes(3));

        String json = service.exportUserData(7L);

        assertThat(json).startsWith("{\"formatVersion\":1,")
                .contains("\"profile\":{\"id\":7,\"name\":\"Ana\"")
                .contains("\"relationships\":[]", "\"plans\":[]", "\"workoutLogs\":[]", "\"exercises\":[]");
    }

    /** Past the size bound the export is refused, never truncated. */
    @Test
    void exportUserData_overTheSizeBound_isRefused() {
        stubAnAccount();
        UserDataExportService service = service(DataSize.ofBytes(50));

        assertThatThrownBy(() -> service.exportUserData(7L))
                .isInstanceOf(ExportTooLargeException.class)
                .hasMessage("Data export too large for one response: the document exceeds 50B");
    }

    @Test
    void exportUserData_forAMissingOrDeletedAccount_isNotFound() {
        when(queries.profile(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(DataSize.ofMegabytes(3)).exportUserData(7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private UserDataExportService service(DataSize maxSize) {
        return new UserDataExportService(queries, new UserDataExportProperties(100, maxSize), jsonMapper);
    }

    private void stubAnAccount() {
        when(queries.profile(7L)).thenReturn(Optional.of(new UserDataExport.Profile(
                7L, "Ana", "ana@vertice.com", null, null, "CLIENT", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"))));
        when(queries.consent(7L)).thenReturn(new UserDataExport.Consent(null, null, null, null));
        when(queries.relationships(7L)).thenReturn(List.of());
        when(queries.plans(7L)).thenReturn(List.of());
        when(queries.workoutLogs(7L)).thenReturn(List.of());
        when(queries.exercises(7L)).thenReturn(List.of());
    }
}
