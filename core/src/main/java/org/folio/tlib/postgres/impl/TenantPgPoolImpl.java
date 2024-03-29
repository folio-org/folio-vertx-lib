package org.folio.tlib.postgres.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.SslMode;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.PrepareOptions;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Query;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.tlib.postgres.TenantPgPool;

/**
 * The {@link PgPool} for a tenant.
 */
public class TenantPgPoolImpl implements TenantPgPool {

  private static final Logger log = LogManager.getLogger(TenantPgPoolImpl.class);
  static Map<PgConnectOptions, PgPool> pgPoolMap = new HashMap<>();

  static String host = System.getenv("DB_HOST");
  static String port = System.getenv("DB_PORT");
  static String user = System.getenv("DB_USERNAME");
  static String password = System.getenv("DB_PASSWORD");
  static String database = System.getenv("DB_DATABASE");
  static String maxPoolSize = System.getenv("DB_MAXPOOLSIZE");
  static String serverPem = System.getenv("DB_SERVER_PEM");
  static String module;
  static PgConnectOptions pgConnectOptions = new PgConnectOptions();

  final String tenant;
  PgPool pgPool;
  JsonObject config;

  final PoolOptions poolOptions;

  static String substTenant(String v, String tenant) {
    return v.replace("{tenant}", tenant);
  }

  static String sanitize(String v) {
    if (v.contains("'") || v.contains("\"")) {
      throw new IllegalArgumentException(v);
    }
    return v.replace("-", "_").replace(".", "_");
  }

  public static PgConnectOptions getDefaultConnectOptions() {
    return TenantPgPoolImpl.pgConnectOptions;
  }

  public static void setDefaultConnectOptions(PgConnectOptions connectOptions) {
    TenantPgPoolImpl.pgConnectOptions = connectOptions;
  }

  public static void setModule(String module) {
    TenantPgPoolImpl.module = module != null ? sanitize(module) : null;
  }

  public static void setServerPem(String serverPem) {
    pgConnectOptions.setSslMode(SslMode.DISABLE);
    TenantPgPoolImpl.serverPem = serverPem;
  }

  public static void setMaxPoolSize(String maxPoolSize) {
    TenantPgPoolImpl.maxPoolSize = maxPoolSize;
  }

  private TenantPgPoolImpl(Vertx vertx, String tenant, PoolOptions poolOptions) {
    config = vertx.getOrCreateContext().config();
    this.tenant = tenant;
    this.poolOptions = poolOptions;
  }

  /**
   * Create pool for Tenant.
   *
   * <p>The returned pool implements PgPool interface so this cab be used like PgPool as usual.
   * PgPool.setModule *must* be called before the queries are executed, since schema is based
   * on module name.
   *
   * @param vertx Vert.x handle
   * @param tenant Tenant
   * @return pool with PgPool semantics
   */
  public static TenantPgPoolImpl tenantPgPool(Vertx vertx, String tenant) {
    if (module == null) {
      throw new IllegalStateException("TenantPgPool.setModule must be called");
    }
    PgConnectOptions connectOptions = pgConnectOptions;
    // overwrite default "vertx-pg-client" shown in pg_stat_activity
    // https://www.postgresql.org/docs/current/runtime-config-logging.html#GUC-APPLICATION-NAME
    connectOptions.getProperties().put("application_name", module);
    if (host != null) {
      connectOptions.setHost(substTenant(host, tenant));
    }
    if (port != null) {
      connectOptions.setPort(Integer.parseInt(port));
    }
    if (user != null) {
      connectOptions.setUser(substTenant(user, tenant));
    }
    if (password != null) {
      connectOptions.setPassword(password);
    }
    if (database != null) {
      connectOptions.setDatabase(substTenant(database, tenant));
    }
    if (serverPem != null) {
      connectOptions.setSslMode(SslMode.VERIFY_FULL);
      connectOptions.setHostnameVerificationAlgorithm("HTTPS");
      connectOptions.setPemTrustOptions(
          new PemTrustOptions().addCertValue(Buffer.buffer(serverPem)));
      connectOptions.setEnabledSecureTransportProtocols(Collections.singleton("TLSv1.3"));
      connectOptions.setOpenSslEngineOptions(new OpenSSLEngineOptions());
    }
    PoolOptions poolOptions = new PoolOptions();
    if (maxPoolSize != null) {
      poolOptions.setMaxSize(Integer.parseInt(maxPoolSize));
    }
    TenantPgPoolImpl tenantPgPool = new TenantPgPoolImpl(vertx, sanitize(tenant), poolOptions);
    tenantPgPool.pgPool = pgPoolMap.computeIfAbsent(connectOptions, key ->
        PgPool.pool(vertx, connectOptions, poolOptions));
    return tenantPgPool;
  }

  @Override
  public String getSchema() {
    return tenant + "_" + module;
  }

  @Override
  public PoolOptions getPoolOptions() {
    return poolOptions;
  }

  @Override
  public Pool getPool() {
    return pgPool;
  }

  @Override
  public void getConnection(Handler<AsyncResult<SqlConnection>> handler) {
    pgPool.getConnection(handler);
  }

  @Override
  public Future<SqlConnection> getConnection() {
    return pgPool.getConnection();
  }

  @Override
  public Query<RowSet<Row>> query(String s) {
    log.debug("query {}", s);
    return pgPool.query(s);
  }

  @Override
  public PreparedQuery<RowSet<Row>> preparedQuery(String s) {
    return preparedQuery(s, null);
  }

  @Override
  public PreparedQuery<RowSet<Row>> preparedQuery(String s, PrepareOptions prepareOptions) {
    log.debug("preparedQuery {}", s);
    return pgPool.preparedQuery(s, prepareOptions);
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    // release our pool from the map
    while (pgPoolMap.values().remove(pgPool)) { }
    pgPool.close(handler);
  }

  @Override
  public Future<Void> close() {
    // release our pool from the map
    while (pgPoolMap.values().remove(pgPool)) { }
    return pgPool.close();
  }

  /**
   * Execute a list of queries.
   *
   * @param queries executed in order; processing is stopped if any queries fail.
   * @return async result.
   */
  @Override
  public Future<Void> execute(List<String> queries) {
    Future<RowSet<Row>> future = Future.succeededFuture();
    for (String cmd : queries) {
      future = future.compose(res -> query(cmd).execute()
          .onFailure(x -> log.warn("{} FAIL: {}", cmd, x.getMessage())));
    }
    return future.mapEmpty();
  }

  /**
   * Execute prepared query.
   *
   * @param sql query
   * @param tuple tuple
   * @return async result rowset
   */
  @Override
  public Future<RowSet<Row>> execute(String sql, Tuple tuple) {
    Future<Void> future = Future.succeededFuture();
    if (Boolean.TRUE.equals(config.getBoolean("explain_analyze"))) {
      future = explainAnalyze(sql, tuple);
    }
    return future.compose(x -> preparedQuery(sql).execute(tuple));
  }

  Future<Void> explainAnalyze(String sql, Tuple tuple) {
    return preparedQuery("EXPLAIN ANALYZE " + sql).execute(tuple)
        .map(rowSet -> {
          StringBuilder e = new StringBuilder(sql);
          for (Row row : rowSet) {
            e.append('\n').append(row.getValue(0));
          }
          log.info(e.toString());
          return null;
        }).mapEmpty();
  }

  @Override
  public PgPool connectHandler(Handler<SqlConnection> handler) {
    return pgPool.connectHandler(handler);
  }

  @Override
  public PgPool connectionProvider(Function<Context, Future<SqlConnection>> function) {
    return pgPool.connectionProvider(function);
  }

  @Override
  public int size() {
    return pgPool.size();
  }

  /**
   * Close all pools.
   *
   * @return async result
   */
  public static Future<Void> closeAll() {
    List<Future<Void>> futures = new ArrayList<>(pgPoolMap.size());
    pgPoolMap.forEach((a, b) -> futures.add(b.close()));
    return GenericCompositeFuture.all(futures)
        .onComplete(x -> pgPoolMap.clear())
        .mapEmpty();
  }
}
