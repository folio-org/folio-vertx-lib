package org.folio.tlib.postgres;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.folio.tlib.postgres.impl.TenantPgPoolImpl;

/**
 * The {@link PgPool} for a tenant.
 */
public interface TenantPgPool extends PgPool {

  /**
   * create tenant pool for tenant.
   *
   * @param vertx vert.x instance
   * @param tenant tenant name.
   * @return pool.
   */
  static TenantPgPool pool(Vertx vertx, @NotNull String tenant) {
    if (tenant == null) {
      throw new IllegalArgumentException("Tenant must not be null");
    }
    return TenantPgPoolImpl.tenantPgPool(vertx, tenant);
  }

  Future<Void> execute(List<String> queries);

  Future<RowSet<Row>> execute(String sql, Tuple tuple);

  Pool getPool();

  String getSchema();

  PoolOptions getPoolOptions();

  static void setDefaultConnectOptions(PgConnectOptions connectOptions) {
    TenantPgPoolImpl.setDefaultConnectOptions(connectOptions);
  }

  static PgConnectOptions getDefaultConnectOptions() {
    return TenantPgPoolImpl.getDefaultConnectOptions();
  }

  static void setModule(String module) {
    TenantPgPoolImpl.setModule(module);
  }

  static void setServerPem(String serverPem) {
    TenantPgPoolImpl.setServerPem(serverPem);
  }

  static void setMaxPoolSize(String maxPoolSize) {
    TenantPgPoolImpl.setMaxPoolSize(maxPoolSize);
  }

  static Future<Void> closeAll() {
    return TenantPgPoolImpl.closeAll();
  }
}
