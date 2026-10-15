package com.vertice.api.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins pgjdbc's {@code logServerErrorDetail=false} (application.properties). Without it, every
 * Postgres error message carries the server's {@code Detail: Key (cpf)=(...)} line, and with it
 * the email or CPF a request collided on, into exceptions and logs (review of #67).
 */
@SpringBootTest(properties = {"spring.grpc.server.port=19122", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class DatasourceErrorDetailTest {

    private static final String CPF = "39053344705";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void theDriverIsConfiguredWithoutServerErrorDetail() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getDataSourceProperties())
                .containsEntry("logServerErrorDetail", "false");
    }

    /** Rolled back: neither row commits. */
    @Test
    void aRealUniqueViolation_namesTheConstraint_butNotTheValue() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            insertClient("first");
            insertClient("second");
        }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains("uq_users_cpf")
                        .doesNotContain("Detail:")
                        .doesNotContain(CPF));
    }

    private void insertClient(String name) {
        jdbc.update("INSERT INTO users (name, email, cpf, role) VALUES (:name, :email, :cpf, 'CLIENT')",
                Map.of("name", name, "email", name + "-" + System.nanoTime() + "@error-detail.example.com", "cpf", CPF));
    }
}
