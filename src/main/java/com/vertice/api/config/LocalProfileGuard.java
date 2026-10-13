package com.vertice.api.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code local} profile turns off REST and gRPC authentication and turns on gRPC reflection,
 * so the application refuses to boot with it anywhere that doesn't look like a developer machine:
 * when {@code VERTICE_ENV} names any environment other than {@code local}/{@code dev}, or when the
 * datasource points at a host other than this machine or the compose {@code postgres} service.
 * Run by {@link BootGuards} whenever {@code local} is among the active profiles, including
 * combinations such as {@code local,prod}, before any bean (datasource, Flyway, servers) exists.
 *
 * <p>{@code postgres} is allowlisted because it is the compose service name, but it may just as well
 * be a deployed database's hostname: deployed environments must set {@code VERTICE_ENV} (e.g.
 * {@code prod}) for this guard to catch them.
 */
public class LocalProfileGuard {

    static final Set<String> ALLOWED_ENVS = Set.of("local", "dev");

    /** This machine, plus {@code postgres}: the service name in vertice-api's and vertice-local's compose files. */
    static final Set<String> ALLOWED_DB_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "postgres");

    private static final String JDBC_PREFIX = "jdbc:postgresql://";

    /** pgjdbc lets these query parameters replace the host written in the URL. */
    private static final Set<String> HOST_OVERRIDE_PARAMS = Set.of("host", "pghost", "service");

    private final String verticeEnv;
    private final String datasourceUrl;

    public LocalProfileGuard(String verticeEnv, String datasourceUrl) {
        this.verticeEnv = verticeEnv == null ? "" : verticeEnv;
        this.datasourceUrl = datasourceUrl;
    }

    // Messages never echo the datasource URL: it may carry a user or password as query parameters.
    void check() {
        String env = verticeEnv.strip().toLowerCase(Locale.ROOT);
        if (!env.isEmpty() && !ALLOWED_ENVS.contains(env)) {
            throw new IllegalStateException(
                    "The local profile disables authentication and must not run with VERTICE_ENV=" + env
                            + ". Remove 'local' from SPRING_PROFILES_ACTIVE, or unset VERTICE_ENV"
                            + " (or set it to local/dev) on a developer machine.");
        }

        List<String> hosts = datasourceHosts(datasourceUrl).orElseThrow(() -> new IllegalStateException(
                "The local profile disables authentication and only runs against a local database, but the"
                        + " host in spring.datasource.url (SPRING_DATASOURCE_URL) could not be determined."
                        + " Use jdbc:postgresql://<host>[:port]/<db> with one of " + ALLOWED_DB_HOSTS
                        + ", without host/service query parameters."));
        for (String host : hosts) {
            if (!ALLOWED_DB_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(
                        "The local profile disables authentication and only runs against a local database,"
                                + " but spring.datasource.url (SPRING_DATASOURCE_URL) points at host '" + host
                                + "'. Use one of " + ALLOWED_DB_HOSTS
                                + ", or remove 'local' from SPRING_PROFILES_ACTIVE.");
            }
        }
    }

    /**
     * Extracts every host from a {@code jdbc:postgresql://host1[:port1][,host2[:port2]...]/db[?params]}
     * URL. Empty when the URL doesn't have that shape (including pgjdbc's host-less forms) or when a
     * query parameter could override the host, so callers fail closed.
     */
    static Optional<List<String>> datasourceHosts(String url) {
        if (url == null || !url.startsWith(JDBC_PREFIX)) {
            return Optional.empty();
        }
        String rest = url.substring(JDBC_PREFIX.length());
        int queryStart = rest.indexOf('?');
        if (queryStart >= 0) {
            if (overridesHost(rest.substring(queryStart + 1))) {
                return Optional.empty();
            }
            rest = rest.substring(0, queryStart);
        }
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        String authority = rest.substring(0, slash);
        if (authority.contains("@")) {
            return Optional.empty();
        }

        List<String> hosts = new ArrayList<>();
        for (String hostAndPort : authority.split(",", -1)) {
            Optional<String> host = hostOf(hostAndPort);
            if (host.isEmpty()) {
                return Optional.empty();
            }
            hosts.add(host.get());
        }
        return Optional.of(hosts);
    }

    /**
     * {@code host}, {@code host:port}, {@code [ipv6]} or {@code [ipv6]:port}. Splits off the port at
     * the last {@code :} after any {@code ]}, as pgjdbc does, so {@code localhost:evil:5432} yields
     * host {@code localhost:evil}.
     */
    private static Optional<String> hostOf(String hostAndPort) {
        int portStart = hostAndPort.lastIndexOf(':');
        String host = portStart >= 0 && hostAndPort.lastIndexOf(']') < portStart
                ? hostAndPort.substring(0, portStart)
                : hostAndPort;
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        } else if (host.contains("[") || host.contains("]")) {
            return Optional.empty();
        }
        return host.isBlank() ? Optional.empty() : Optional.of(host);
    }

    private static boolean overridesHost(String query) {
        for (String param : query.split("&")) {
            String key = param.split("=", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (HOST_OVERRIDE_PARAMS.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
