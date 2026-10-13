package com.vertice.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalProfileGuardTest {

    private static final String LOCAL_URL = "jdbc:postgresql://localhost:5432/vertice";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LocalProfileGuard.class)
            .withPropertyValues("spring.profiles.active=local", "spring.datasource.url=" + LOCAL_URL);

    @Test
    void unsetEnv_localhostDatasource_starts() {
        contextRunner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(LocalProfileGuard.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", " DEV ", "Local", ""})
    void allowedEnv_starts(String env) {
        contextRunner.withPropertyValues("VERTICE_ENV=" + env)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "PROD", " staging "})
    void otherEnv_refusesToStart(String env) {
        contextRunner.withPropertyValues("VERTICE_ENV=" + env)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("must not run with VERTICE_ENV="));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://postgres:5432/vertice",
            "jdbc:postgresql://127.0.0.1:55432/vertice",
            "jdbc:postgresql://[::1]:5432/vertice",
            "jdbc:postgresql://LOCALHOST/vertice?sslmode=disable",
            "jdbc:postgresql://localhost:5432,postgres:5432/vertice?targetServerType=primary"})
    void localDatasource_starts(String url) {
        contextRunner.withPropertyValues("spring.datasource.url=" + url)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void remoteDatasource_refusesToStartWithoutLeakingCredentials() {
        contextRunner.withPropertyValues(
                        "spring.datasource.url=jdbc:postgresql://db.example.com:5432/vertice?password=s3cr3t")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("points at host 'db.example.com'")
                        .message().doesNotContain("s3cr3t"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432,db.example.com:5432/vertice",
            "jdbc:postgresql://10.0.0.5/vertice",
            "jdbc:postgresql://localhost.example.com/vertice"})
    void anyRemoteHost_refusesToStart(String url) {
        contextRunner.withPropertyValues("spring.datasource.url=" + url)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/vertice?host=db.example.com",
            "jdbc:postgresql://localhost:5432/vertice?ssl=true&PGHOST=db.example.com",
            "jdbc:postgresql://localhost:5432/vertice?service=prod",
            "jdbc:postgresql://user:s3cr3t@localhost/vertice",
            "jdbc:postgresql:///vertice",
            "jdbc:postgresql:vertice",
            "jdbc:postgresql://localhost",
            "jdbc:postgresql://[::1/vertice",
            "jdbc:h2:mem:vertice",
            ""})
    void unparseableDatasource_failsClosedWithoutLeakingCredentials(String url) {
        contextRunner.withPropertyValues("spring.datasource.url=" + url)
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("could not be determined")
                        .message().doesNotContain("s3cr3t"));
    }

    @Test
    void nonLocalProfile_doesNotLoadGuard() {
        contextRunner.withPropertyValues("spring.profiles.active=prod", "VERTICE_ENV=prod",
                        "spring.datasource.url=jdbc:postgresql://db.example.com/vertice")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(LocalProfileGuard.class));
    }

    @Test
    void localCombinedWithAnotherProfile_stillGuarded() {
        contextRunner.withPropertyValues("spring.profiles.active=local,prod", "VERTICE_ENV=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void datasourceHosts_extractsEveryHost() {
        assertThat(LocalProfileGuard.datasourceHosts("jdbc:postgresql://[::1]:5432,postgres,db:6543/vertice?ssl=true"))
                .contains(List.of("::1", "postgres", "db"));
    }
}
