package org.folio.tlib.api;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import org.folio.tlib.RouterCreator;

/**
 * Implements {@code GET /admin/health}.
 */
public class HealthApi implements RouterCreator {
  @Override
  public Future<Router> createRouter(Vertx vertx) {
    Router router = Router.router(vertx);
    router.route(HttpMethod.GET, "/admin/health").handler(ctx -> {
      ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
      ctx.response().end("OK");
    });
    return Future.succeededFuture(router);
  }
}
