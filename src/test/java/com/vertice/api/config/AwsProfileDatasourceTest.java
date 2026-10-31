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
import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The `aws` profile (application-aws.properties) runs the datasource through the AWS Advanced JDBC
 * Wrapper. Against the local Postgres, with the IAM plugin and certificate verification switched off,
 * this pins what the deploy relies on: Flyway and {@code ddl-auto=validate} work through the wrapper
 * (the context boots), and pgjdbc still receives {@code logServerErrorDetail=false}, so no email or CPF
 * reaches an exception message (see DatasourceErrorDetailTest for the plain-pgjdbc path).
 */
@SpringBootTest(properties = {
        "spring.grpc.server.port=19128",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.url=jdbc:aws-wrapper:postgresql://localhost:5432/vertice",
        "spring.datasource.username=vertice",
        "spring.datasource.password=vertice",
        "spring.datasource.hikari.data-source-properties.wrapperPlugins=",
        "spring.datasource.hikari.data-source-properties.sslmode=disable",
        "vertice.jwt.secret=aws-profile-test-secret-0123456789-abcdefghij"})
@ActiveProfiles("aws")
class AwsProfileDatasourceTest {

    private static final String CPF = "52998224725";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void connectionsGoThroughTheWrapper_withoutServerErrorDetail() throws Exception {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getDataSourceProperties())
                .containsEntry("logServerErrorDetail", "false");
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDriverName()).contains("Advanced JDBC Wrapper");
        }
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
                Map.of("name", name, "email", name + "-" + System.nanoTime() + "@aws-profile.example.com", "cpf", CPF));
    }
}
