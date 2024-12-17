package org.folio.tlib.postgres;

import io.vertx.pgclient.PgConnectOptions;
import org.testcontainers.containers.PostgreSQLContainer;

public final class TenantPgPoolContainer {
  public static final String DEFAULT_IMAGE_NAME = "postgres:16-alpine";
  private static final String IMAGE_NAME = System.getenv()
      .getOrDefault("TESTCONTAINERS_POSTGRES_IMAGE", DEFAULT_IMAGE_NAME);

  public static PostgreSQLContainer<?> create() {
    return create(IMAGE_NAME);
  }

  public static PostgreSQLContainer<?> create(String image) {
    PostgreSQLContainer<?> postgresSQLContainer = new PostgreSQLContainer<>(image);
    postgresSQLContainer.start();

    TenantPgPool.setDefaultConnectOptions(new PgConnectOptions()
        .setPort(postgresSQLContainer.getFirstMappedPort())
        .setHost(postgresSQLContainer.getHost())
        .setDatabase(postgresSQLContainer.getDatabaseName())
        .setUser(postgresSQLContainer.getUsername())
        .setPassword(postgresSQLContainer.getPassword()));
    return postgresSQLContainer;
  }
}
