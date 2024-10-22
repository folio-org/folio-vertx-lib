package org.folio.tlib.postgres.testing;

import io.vertx.pgclient.PgConnectOptions;
import java.util.Map;
import org.folio.tlib.postgres.TenantPgPool;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A {@link PostgreSQLContainer} that sets
 * {@link TenantPgPool#setDefaultConnectOptions}.
 */
public final class TenantPgPoolContainer {
  private static final String POSTGRES_IMAGE = postgresImage(System.getenv());

  /**
   * Create PostgreSQL container for TenantPgPool.
   *
   * @return container.
   */
  public static PostgreSQLContainer<?> create() {
    return create(POSTGRES_IMAGE);
  }

  /**
   * Create PostgreSQL container for TenantPgPool.
   *
   * @param image container image name.
   * @return container.
   */
  public static PostgreSQLContainer<?> create(String image) {
    PostgreSQLContainer<?> container = new PostgreSQLContainer<>(image);
    container.start();

    TenantPgPool.setDefaultConnectOptions(new PgConnectOptions()
        .setPort(container.getFirstMappedPort())
        .setHost(container.getHost())
        .setDatabase(container.getDatabaseName())
        .setUser(container.getUsername())
        .setPassword(container.getPassword()));
    return container;
  }

  /**
   * PostgreSQL container image name from TESTCONTAINERS_POSTGRES_IMAGE
   * environment variable, or fall-back name.
   *
   * @see <a href="https://wiki.folio.org/display/TC/DR-000037+-+TESTCONTAINERS_POSTGRES_IMAGE">
   *     DR-000037 - TESTCONTAINERS_POSTGRES_IMAGE</a>
   */
  static String postgresImage(Map<String, String> env) {
    return env.getOrDefault("TESTCONTAINERS_POSTGRES_IMAGE", "postgres:16-alpine");
  }
}
