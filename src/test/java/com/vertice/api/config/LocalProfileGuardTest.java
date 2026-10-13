package com.vertice.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalProfileGuardTest {

    private static final String LOCAL_URL = "jdbc:postgresql://localhost:5432/vertice";
    private static final String REMOTE_URL = "jdbc:postgresql://db.example.invalid:5432/vertice";

    @Test
    void unsetEnv_localhostDatasource_passes() {
        assertPasses("", LOCAL_URL);
        assertPasses(null, LOCAL_URL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", " DEV ", "Local", "  "})
    void allowedEnv_passes(String env) {
        assertPasses(env, LOCAL_URL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "PROD", " staging "})
    void otherEnv_fails(String env) {
        assertRefused(env, LOCAL_URL, "must not run with VERTICE_ENV=");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://postgres:5432/vertice",
            "jdbc:postgresql://127.0.0.1:55432/vertice",
            "jdbc:postgresql://[::1]:5432/vertice",
            "jdbc:postgresql://[::1]/vertice",
            "jdbc:postgresql://LOCALHOST/vertice?sslmode=disable",
            "jdbc:postgresql://localhost:5432,postgres:5432/vertice?targetServerType=primary"})
    void localDatasource_passes(String url) {
        assertPasses("", url);
    }

    @Test
    void remoteDatasource_failsWithoutLeakingCredentials() {
        assertThatThrownBy(new LocalProfileGuard("", REMOTE_URL + "?password=s3cr3t")::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("points at host 'db.example.invalid'")
                .message().doesNotContain("s3cr3t");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432,db.example.com:5432/vertice",
            "jdbc:postgresql://10.0.0.5/vertice",
            "jdbc:postgresql://localhost.example.com/vertice",
            // pgjdbc splits the port at the last ':', so this host is "localhost:evil.example.com".
            "jdbc:postgresql://localhost:evil.example.com:5432/vertice"})
    void anyRemoteHost_fails(String url) {
        assertRefused("", url, "points at host");
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
            "jdbc:postgresql://loc[al]host/vertice",
            "jdbc:h2:mem:vertice",
            ""})
    void unparseableDatasource_failsClosedWithoutLeakingCredentials(String url) {
        assertThatThrownBy(new LocalProfileGuard("", url)::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be determined")
                .message().doesNotContain("s3cr3t");
    }

    @Test
    void datasourceHosts_extractsEveryHost() {
        assertThat(LocalProfileGuard.datasourceHosts("jdbc:postgresql://[::1]:5432,postgres,db:6543/vertice?ssl=true"))
                .contains(List.of("::1", "postgres", "db"));
    }

    // Wiring through BootGuards: these boot a real SpringApplication, which runs it from spring.factories.

    @Test
    void localProfile_otherEnv_refusesToStart() {
        assertThatThrownBy(() -> boot(EmptyConfig.class, "--spring.profiles.active=local", "--VERTICE_ENV=prod",
                "--spring.datasource.url=" + LOCAL_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not run with VERTICE_ENV=prod");
    }

    @Test
    void localProfile_lazyInitialization_stillRefusesToStart() {
        assertThatThrownBy(() -> boot(EmptyConfig.class, "--spring.profiles.active=local", "--VERTICE_ENV=prod",
                "--spring.datasource.url=" + LOCAL_URL, "--spring.main.lazy-initialization=true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not run with VERTICE_ENV=prod");
    }

    @Test
    void localProfile_remoteDatasource_refusesBeforeConnectingOrMigrating() {
        // With DataSource + Flyway auto-config, a late guard would surface a connection error from
        // Flyway instead; failing with the guard's own exception (no cause) proves it ran first.
        assertThatThrownBy(() -> boot(DataSourceAndFlywayConfig.class, "--spring.profiles.active=local",
                "--VERTICE_ENV=", "--spring.datasource.url=" + REMOTE_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("points at host 'db.example.invalid'")
                .hasNoCause();
    }

    @Test
    void localCombinedWithAnotherProfile_stillGuarded() {
        assertThatThrownBy(() -> boot(EmptyConfig.class, "--spring.profiles.active=local,prod", "--VERTICE_ENV=prod",
                "--spring.datasource.url=" + LOCAL_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not run with VERTICE_ENV=prod");
    }

    @Test
    void localProfile_localEnvironment_starts() {
        assertThatCode(() -> boot(EmptyConfig.class, "--spring.profiles.active=local", "--VERTICE_ENV=",
                "--spring.datasource.url=" + LOCAL_URL).close())
                .doesNotThrowAnyException();
    }

    @Test
    void nonLocalProfile_doesNotRunGuard() {
        assertThatCode(() -> boot(EmptyConfig.class, "--spring.profiles.active=prod", "--VERTICE_ENV=prod",
                "--spring.datasource.url=" + REMOTE_URL,
                "--vertice.jwt.secret=" + "x".repeat(JwtSecretGuard.MIN_SECRET_BYTES)).close())
                .doesNotThrowAnyException();
    }

    private static void assertPasses(String env, String url) {
        assertThatCode(new LocalProfileGuard(env, url)::check).doesNotThrowAnyException();
    }

    private static void assertRefused(String env, String url, String message) {
        assertThatThrownBy(new LocalProfileGuard(env, url)::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    /** Command-line args, so they win over any VERTICE_ENV/SPRING_DATASOURCE_URL in the developer's shell. */
    private static ConfigurableApplicationContext boot(Class<?> config, String... args) {
        return new SpringApplicationBuilder(config)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    static class DataSourceAndFlywayConfig {
    }
}
