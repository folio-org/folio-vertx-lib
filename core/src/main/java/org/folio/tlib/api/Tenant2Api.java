package org.folio.tlib.api;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;
import io.vertx.openapi.validation.ValidatedRequest;
import io.vertx.sqlclient.Tuple;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitConf;
import org.folio.tlib.TenantInitHooks;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.tlib.util.TenantUtil;

/**
 * Implements {@code /_/tenant} APIs that create, update or delete
 * the database schema and tables of a tenant.
 */
public class Tenant2Api implements RouterCreator {
  private static final Logger log = LogManager.getLogger(Tenant2Api.class);

  private final Map<UUID, List<Promise<Void>>> waiters = new HashMap<>();

  private final TenantInitHooks hooks;

  public Tenant2Api(TenantInitHooks hooks) {
    this.hooks = hooks;
  }

  static void failHandler(RoutingContext ctx, int code, String msg) {
    ctx.response().setStatusCode(code);
    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
    ctx.response().end(msg != null ? msg : "Failure");
  }

  /**
   * Error handler which produces HTTP response on Routing Context.
   *
   * <p>If throwable is not null error is logged, but not the stacktrace.
   *
   * @param ctx Routing context
   * @param code error code if throwable is non-null
   * @param e cause; if null, then the status code on routing context is used.
   */
  static void failHandler(RoutingContext ctx, int code, Throwable e) {
    if (e == null) {
      // assume the status code error is stored in routing context
      failHandler(ctx, ctx.statusCode(),
          HttpResponseStatus.valueOf(ctx.statusCode()).reasonPhrase());
    } else {
      Throwable t = e.getCause();
      if (t == null) {
        t = e;
      }
      log.error("{}", e.getMessage(), e);
      failHandler(ctx, code, t.getMessage());
    }
  }

  private void runAsync(TenantInitConf tenantInitConf, JsonObject tenantJob) {
    UUID jobId = UUID.fromString(tenantJob.getString("id"));
    hooks.postInit(tenantInitConf)
        .onComplete(x -> {
          tenantJob.put("complete", true);
          if (x.failed()) {
            String msg = x.cause().getMessage();
            if (msg == null) {
              msg = x.cause().getClass().getName();
            }
            tenantJob.put("error", msg);
          }
          updateJob(tenantInitConf.vertx(), tenantJob)
              .onComplete(y -> {
                List<Promise<Void>> promises = waiters.remove(jobId);
                if (promises != null) {
                  for (Promise<Void> promise : promises) {
                    promise.tryComplete();
                  }
                }
              });
        });
  }

  private Future<JsonObject> createJob(TenantInitConf tenantInitConf) {
    var vertx = tenantInitConf.vertx();
    var tenant = tenantInitConf.tenant();
    var tenantAttributes = tenantInitConf.tenantAttributes();
    log.info("postTenant got {}", tenantAttributes.encode());
    TenantPgPool tenantPgPool = TenantPgPool.pool(vertx, tenant);
    String schema = tenantPgPool.getSchema();
    return hooks.preInit(tenantInitConf)
        .compose(res -> {
          if (Boolean.TRUE.equals(tenantAttributes.getBoolean("purge"))) {
            return tenantPgPool.execute(List.of(
                "DROP SCHEMA IF EXISTS " + schema + " CASCADE",
                "DROP ROLE IF EXISTS " + schema
            )).map((JsonObject) null);
          }
          return tenantPgPool.query("SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE"
              + " nspname = '" + schema + "')")
              .execute()
              .map(rowSet -> rowSet.iterator().next().getBoolean(0))
              .compose(exists -> {
                if (exists) {
                  return Future.succeededFuture();
                }
                return tenantPgPool.execute(List.of(
                    "CREATE ROLE " + schema + " PASSWORD 'tenant'"
                        + " NOSUPERUSER NOCREATEDB INHERIT LOGIN",
                    "GRANT " + schema + " TO CURRENT_USER",
                    "CREATE SCHEMA " + schema + " AUTHORIZATION " + schema
                ));
              })
              .compose(res1 ->
                  tenantPgPool.query("CREATE TABLE IF NOT EXISTS " + schema + ".job "
                          + "(id UUID PRIMARY KEY, jsonb JSONB NOT NULL)")
                      .execute()
              )
              .compose(res1 -> {
                JsonObject tenantJob = new JsonObject();
                tenantJob.put("id", UUID.randomUUID().toString());
                tenantJob.put("complete", false);
                tenantJob.put("tenant", tenant);
                tenantJob.put("tenantAttributes", tenantAttributes);
                return saveJob(vertx, tenantJob)
                    .onSuccess(x -> runAsync(tenantInitConf, tenantJob))
                    .map(tenantJob);
              });
        });
  }

  private Future<JsonObject> getJob(Vertx vertx, String tenant, UUID jobId, int secondsToWait) {
    TenantPgPool tenantPgPool = TenantPgPool.pool(vertx, tenant);
    return tenantPgPool.preparedQuery("SELECT jsonb FROM "
            + tenantPgPool.getSchema() + ".job WHERE ID = $1")
        .execute(Tuple.of(jobId))
        .compose(res -> {
          if (!res.iterator().hasNext()) {
            return Future.succeededFuture(null);
          }
          JsonObject tenantJob = res.iterator().next().getJsonObject(0);
          if (secondsToWait > 0 && !Boolean.TRUE.equals(tenantJob.getBoolean("complete"))) {
            Promise<Void> promise = Promise.promise();
            waiters.putIfAbsent(jobId, new LinkedList<>());
            waiters.get(jobId).add(promise);
            vertx.setTimer(secondsToWait * 1000L, res1 -> promise.tryComplete());
            return promise.future().compose(res1 -> getJob(vertx, tenant, jobId, 0));
          }
          return Future.succeededFuture(tenantJob);
        });
  }

  private static Future<Boolean> deleteJob(Vertx vertx, String tenant, UUID jobId) {
    TenantPgPool tenantPgPool = TenantPgPool.pool(vertx, tenant);
    String schema = tenantPgPool.getSchema();
    return tenantPgPool.preparedQuery("DELETE FROM " + schema + ".job WHERE ID = $1")
        .execute(Tuple.of(jobId))
        .map(res -> (res.rowCount() > 0));
  }

  private static Future<Void> updateJob(Vertx vertx, JsonObject tenantJob) {
    String tenant = tenantJob.getString("tenant");
    UUID jobId = UUID.fromString(tenantJob.getString("id"));
    TenantPgPool tenantPgPool = TenantPgPool.pool(vertx, tenant);
    String schema = tenantPgPool.getSchema();
    return tenantPgPool.preparedQuery("UPDATE " + schema + ".job SET jsonb = $2 WHERE id = $1")
        .execute(Tuple.of(jobId, tenantJob)).mapEmpty();
  }

  private static Future<Void> saveJob(Vertx vertx, JsonObject tenantJob) {
    String tenant = tenantJob.getString("tenant");
    UUID jobId = UUID.fromString(tenantJob.getString("id"));
    TenantPgPool tenantPgPool = TenantPgPool.pool(vertx, tenant);
    String schema = tenantPgPool.getSchema();
    return tenantPgPool.preparedQuery("INSERT INTO " + schema + ".job VALUES ($1, $2)")
        .execute(Tuple.of(jobId, tenantJob)).mapEmpty();
  }

  private void handlers(Vertx vertx, RouterBuilder routerBuilder) {
    log.info("setting up tenant handlers ... begin");

    routerBuilder.getRoute("postTenant")
        .addHandler(ctx -> {
          ValidatedRequest validatedRequest =
              ctx.get(RouterBuilder.KEY_META_DATA_VALIDATED_REQUEST);
          JsonObject tenantAttributes = validatedRequest.getBody().getJsonObject();
          log.info("postTenant handler {}", tenantAttributes.encode());
          var tenantInitConf = new TenantInitConf(vertx, ctx.request().headers(), tenantAttributes);

          createJob(tenantInitConf)
              .onSuccess(tenantJob -> {
                if (tenantJob == null) {
                  ctx.response().setStatusCode(204);
                  ctx.response().end();
                  return;
                }
                ctx.response().setStatusCode(201);
                ctx.response().putHeader(HttpHeaders.LOCATION,
                    "/_/tenant/" + tenantJob.getString("id"));
                ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                ctx.response().end(tenantJob.encode());
              })
              .onFailure(e -> {
                if (e.getClass().getName().contains("ConnectException")) {
                  failHandler(ctx, 400, e.getMessage()
                      + " DB_HOST=" + System.getenv("DB_HOST")
                      + " DB_PORT=" + System.getenv("DB_PORT"));
                  return;
                }
                failHandler(ctx, 500, e);
              });
        })
        .addFailureHandler(ctx -> Tenant2Api.failHandler(ctx, 400, ctx.failure()));

    routerBuilder.getRoute("getTenantJob")
        .addHandler(ctx -> {
          String id = ctx.pathParam("id");
          String tenant = TenantUtil.tenant(ctx);
          List<String> waitParameter = ctx.queryParam("wait");
          int wait = waitParameter.isEmpty() ? 0 : Integer.parseInt(waitParameter.get(0));
          log.info("getTenantJob handler id={} wait={}", id, wait);
          getJob(vertx, tenant, UUID.fromString(id), wait)
              .onSuccess(res -> {
                if (res == null) {
                  failHandler(ctx, 404, "Not found: " + id);
                  return;
                }
                ctx.response().setStatusCode(200);
                ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                ctx.response().end(res.encode());
              })
              .onFailure(e -> failHandler(ctx, 500, e));
        })
        .addFailureHandler(ctx -> Tenant2Api.failHandler(ctx, 400, ctx.failure()));

    routerBuilder.getRoute("deleteTenantJob")
        .addHandler(ctx -> {
          String id = ctx.pathParam("id");
          String tenant = TenantUtil.tenant(ctx);
          log.info("deleteTenantJob handler id={}", id);
          deleteJob(vertx, tenant, UUID.fromString(id))
              .onSuccess(res -> {
                if (Boolean.FALSE.equals(res)) {
                  failHandler(ctx, 404, "Not found: " + id);
                  return;
                }
                ctx.response().setStatusCode(204);
                ctx.response().end();
              })
              .onFailure(e -> failHandler(ctx, 500, e));
        })
        .addFailureHandler(ctx -> Tenant2Api.failHandler(ctx, 400, ctx.failure()));

    log.info("setting up tenant handlers ... done");
  }

  /**
   * Create router for tenant API.
   *
   * @param vertx Vert.x handle
   * @return async result: router
   */
  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return OpenAPIContract.from(vertx,  "openapi/tenant-2.0.yaml")
      .map(contract -> {
        RouterBuilder routerBuilder = RouterBuilder.create(vertx, contract);
        handlers(vertx, routerBuilder);
        return routerBuilder.createRouter();
      });
  }
}
