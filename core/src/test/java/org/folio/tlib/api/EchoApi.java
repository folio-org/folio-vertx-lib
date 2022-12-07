package org.folio.tlib.api;

import io.netty.handler.codec.http.HttpStatusClass;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.folio.tlib.RouterCreator;

public class EchoApi implements RouterCreator {

  static void handleError(RoutingContext ctx, int status, Throwable t) {
    if (t == null) {
      handleError(ctx, ctx.statusCode(), "");
    } else {
      handleError(ctx, status, t.getMessage());
    }
  }

  static void handleError(RoutingContext ctx, int status, String msg) {
    ctx.response().setStatusCode(status);
    ctx.response().putHeader("Content-Type", "text/plain");
    ctx.response().end(msg);
  }

  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return RouterBuilder.create(vertx, "openapi/echo.yaml")
        .map(routerBuilder -> {
          // https://vertx.io/docs/vertx-web/java/#_limiting_body_size
          routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(65536)); // 64 kb
          routerBuilder
              .operation("echo") // operationId in spec
              .handler(ctx -> echo(ctx)
                  .onFailure(e -> handleError(ctx, 500, e))
              )
              .failureHandler(ctx -> handleError(ctx, 400, ctx.failure()));
          return routerBuilder.createRouter();
        });
  }

  Future<Void> echo(RoutingContext ctx) {
    ctx.response().setStatusCode(200);
    ctx.response().putHeader("Content-Type",
        ctx.request().getHeader("Content-Type"));
    ctx.response().end(ctx.body().buffer());
    return Future.succeededFuture();
  }
}
