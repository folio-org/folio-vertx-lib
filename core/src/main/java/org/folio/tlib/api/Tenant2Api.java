package org.folio.tlib.api;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.sqlclient.Tuple;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;
import org.folio.tlib.postgres.impl.TenantPgPoolImpl;

public class Tenant2Api implements RouterCreator {
  private static final Logger log = LogManager.getLogger(Tenant2Api.class);

  private final Map<UUID, List<Promise<Void>>> waiters = new HashMap<>();

  private final TenantInitHooks hooks;

  public Tenant2Api(TenantInitHooks hooks) {
    this.hooks = hooks;
  }

  static void failHandlerText(RoutingContext ctx, int code, String msg) {
    ctx.response().setStatusCode(code);
    ctx.response().putHeader("Content-Type", "text/plain");
    ctx.response().end(msg != null ? msg : "Failure");
  }

  static void failHandler400(RoutingContext ctx, String msg) {
    failHandlerText(ctx, 400, msg);
  }

  static void failHandler404(RoutingContext ctx, String msg) {
    failHandlerText(ctx, 404, msg);
  }

  static void failHandler500(RoutingContext ctx, Throwable e) {
    log.error("{}", e.getMessage(), e);
    failHandlerText(ctx, 500, e.getMessage());
  }

  private void runAsync(Vertx vertx, JsonObject tenantJob) {
    UUID jobId = UUID.fromString(tenantJob.getString("id"));
    hooks.postInit(vertx, tenantJob.getString("tenant"),
        tenantJob.getJsonObject("tenantAttributes"))
        .onComplete(x -> {
          tenantJob.put("complete", true);
          if (x.failed()) {
            String msg = x.cause().getMessage();
            if (msg == null) {
              msg = x.cause().getClass().getName();
            }
            tenantJob.put("error", msg);
          }
          updateJob(vertx, tenantJob)
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

  private Future<JsonObject> createJob(Vertx vertx, String tenant,
                                       JsonObject tenantAttributes) {
    log.info("postTenant got {}", tenantAttributes.encode());
    TenantPgPoolImpl tenantPgPool = TenantPgPoolImpl.tenantPgPool(vertx, tenant);
    return hooks.preInit(vertx, tenant, tenantAttributes)
        .compose(res -> {
          if (Boolean.TRUE.equals(tenantAttributes.getBoolean("purge"))) {
            return tenantPgPool.execute(List.of(
                "DROP SCHEMA IF EXISTS {schema} CASCADE",
                "DROP ROLE IF EXISTS {schema}"
            )).map((JsonObject) null);
          }
          return tenantPgPool.query("SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE"
              + " nspname = '{schema}')")
              .execute()
              .map(rowSet -> rowSet.iterator().next().getBoolean(0))
              .compose(exists -> {
                if (exists) {
                  return Future.succeededFuture();
                }
                return tenantPgPool.execute(List.of(
                    "CREATE ROLE {schema} PASSWORD 'tenant' NOSUPERUSER NOCREATEDB INHERIT LOGIN",
                    "GRANT {schema} TO CURRENT_USER",
                    "CREATE SCHEMA {schema} AUTHORIZATION {schema}"
                ));
              })
              .compose(res1 ->
                  tenantPgPool.query("CREATE TABLE IF NOT EXISTS {schema}.job "
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
                    .onSuccess(x -> runAsync(vertx, tenantJob))
                    .map(tenantJob);
              });
        });
  }

  private Future<JsonObject> getJob(Vertx vertx, String tenant, UUID jobId, int secondsToWait) {
    TenantPgPoolImpl tenantPgPool = TenantPgPoolImpl.tenantPgPool(vertx, tenant);
    return tenantPgPool.preparedQuery("SELECT jsonb FROM {schema}.job WHERE ID = $1")
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
    TenantPgPoolImpl tenantPgPool = TenantPgPoolImpl.tenantPgPool(vertx, tenant);
    return tenantPgPool.preparedQuery("DELETE FROM {schema}.job WHERE ID = $1")
        .execute(Tuple.of(jobId))
        .map(res -> (res.rowCount() > 0));
  }

  private static Future<Void> updateJob(Vertx vertx, JsonObject tenantJob) {
    String tenant = tenantJob.getString("tenant");
    UUID jobId = UUID.fromString(tenantJob.getString("id"));
    TenantPgPoolImpl tenantPgPool = TenantPgPoolImpl.tenantPgPool(vertx, tenant);
    return tenantPgPool.preparedQuery("UPDATE {schema}.job SET jsonb = $2 WHERE id = $1")
        .execute(Tuple.of(jobId, tenantJob)).mapEmpty();
  }

  private static Future<Void> saveJob(Vertx vertx, JsonObject tenantJob) {
    String tenant = tenantJob.getString("tenant");
    UUID jobId = UUID.fromString(tenantJob.getString("id"));
    TenantPgPoolImpl tenantPgPool = TenantPgPoolImpl.tenantPgPool(vertx, tenant);
    return tenantPgPool.preparedQuery("INSERT INTO {schema}.job VALUES ($1, $2)")
        .execute(Tuple.of(jobId, tenantJob)).mapEmpty();
  }

  private void handlers(Vertx vertx, RouterBuilder routerBuilder) {
    log.info("setting up tenant handlers ... begin");
    routerBuilder
        .operation("postTenant")
        .handler(ctx -> {
          RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
          log.info("postTenant handler {}", params.toJson().encode());
          JsonObject tenantAttributes = ctx.getBodyAsJson();
          String tenant = params.headerParameter(XOkapiHeaders.TENANT).getString();
          createJob(vertx, tenant, tenantAttributes)
              .onSuccess(tenantJob -> {
                if (tenantJob == null) {
                  ctx.response().setStatusCode(204);
                  ctx.response().end();
                  return;
                }
                ctx.response().setStatusCode(201);
                ctx.response().putHeader("Location", "/_/tenant/" + tenantJob.getString("id"));
                ctx.response().putHeader("Content-Type", "application/json");
                ctx.response().end(tenantJob.encode());
              })
              .onFailure(e -> {
                if (e.getClass().getName().contains("ConnectException")) {
                  failHandler400(ctx, e.getMessage()
                      + " DB_HOST=" + System.getenv("DB_HOST")
                      + " DB_PORT=" + System.getenv("DB_PORT"));
                  return;
                }
                failHandler500(ctx, e);
              });
        })
        .failureHandler(ctx -> Tenant2Api.failHandlerText(ctx, 400, ctx.failure().getMessage()));
    routerBuilder
        .operation("getTenantJob")
        .handler(ctx -> {
          RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
          String id = params.pathParameter("id").getString();
          String tenant = params.headerParameter(XOkapiHeaders.TENANT).getString();
          RequestParameter waitParameter = params.queryParameter("wait");
          int wait = waitParameter != null ? waitParameter.getInteger() : 0;
          log.info("getTenantJob handler id={} wait={}", id,
              waitParameter != null ? waitParameter.getInteger() : "null");
          getJob(vertx, tenant, UUID.fromString(id), wait)
              .onSuccess(res -> {
                if (res == null) {
                  failHandler404(ctx, "Not found: " + id);
                  return;
                }
                ctx.response().setStatusCode(200);
                ctx.response().putHeader("Content-Type", "application/json");
                ctx.response().end(res.encode());
              })
              .onFailure(e -> failHandler500(ctx, e));
        })
        .failureHandler(ctx -> Tenant2Api.failHandlerText(ctx, 400, ctx.failure().getMessage()));
    routerBuilder
        .operation("deleteTenantJob")
        .handler(ctx -> {
          RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
          String id = params.pathParameter("id").getString();
          String tenant = params.headerParameter(XOkapiHeaders.TENANT).getString();
          log.info("deleteTenantJob handler id={}", id);
          deleteJob(vertx, tenant, UUID.fromString(id))
              .onSuccess(res -> {
                if (Boolean.FALSE.equals(res)) {
                  failHandler404(ctx, "Not found: " + id);
                  return;
                }
                ctx.response().setStatusCode(204);
                ctx.response().end();
              })
              .onFailure(e -> failHandler500(ctx, e));
        })
        .failureHandler(ctx -> Tenant2Api.failHandlerText(ctx, 400, ctx.failure().getMessage()));
    log.info("setting up tenant handlers ... done");
  }

  /**
   * Create router for tenant API.
   * @param vertx Vert.x handle
   * @return async result: router
   */
  @Override
  public Future<Router> createRouter(Vertx vertx, WebClient webClient) {
    return RouterBuilder.create(vertx, "openapi/tenant-2.0.yaml")
        .map(routerBuilder -> {
          handlers(vertx, routerBuilder);
          return routerBuilder.createRouter();
        });
  }

}
