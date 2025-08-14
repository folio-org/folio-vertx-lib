package org.folio.tlib.api;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

import org.folio.tlib.OpenApiRef;
import org.folio.tlib.RouterCreator;

public class EchoApi implements RouterCreator {

  static final int BODY_LIMIT = 65536; // 64 kb as an example of reasonable limit for Json content

  static void handleError(RoutingContext ctx, int status, Throwable t) {
    if (t == null) {
      handleError(ctx, ctx.statusCode(), "echo service status " + ctx.statusCode());
    } else {
      handleError(ctx, status, t.getMessage());
    }
  }

  static void handleError(RoutingContext ctx, int status, String msg) {
    ctx.response().setStatusCode(status);
    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
    ctx.response().end(msg);
  }

  @Override
  public Future<Router> createRouter(Vertx vertx) {
    String spec;
    try {
      spec = OpenApiRef.fix("openapi/echo.yaml");
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
    return OpenAPIContract.from(vertx, spec)
      .map(contract -> {
        RouterBuilder routerBuilder = RouterBuilder.create(vertx, contract);
        //routerBuilder.map(routerBuilder -> {
        // https://vertx.io/docs/vertx-web/java/#_limiting_body_size
        routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(BODY_LIMIT));
        routerBuilder.getRoute("echo") // operationId in spec
          .addHandler(ctx -> echo(ctx)
              .onFailure(e -> handleError(ctx, 500, e))
          )
          .addFailureHandler(ctx -> handleError(ctx, 400, ctx.failure()));
        return routerBuilder.createRouter();
      });
  }

  Future<Void> echo(RoutingContext ctx) {
    ctx.response().setStatusCode(200);
    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE,
        ctx.request().getHeader(HttpHeaders.CONTENT_TYPE));
    ctx.response().end(ctx.body().buffer());
    return Future.succeededFuture();
  }
}
