package org.folio.tlib;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public interface RouterCreator {

  Future<Router> createRouter(Vertx vertx);

  /**
   * Create router for a list of RouterCreators.
   * @param vertx Vertx. handle
   * @param routerCreators list of router creators
   * @return async result with combined router.
   */
  static Future<Router> mountAll(Vertx vertx, RouterCreator [] routerCreators) {
    Future<Void> future = Future.succeededFuture();
    Router router = Router.router(vertx);
    for (RouterCreator routerCreator : routerCreators) {
      future = future.compose(x -> routerCreator.createRouter(vertx))
          .map(subRouter -> {
            router.mountSubRouter("/", subRouter);
            return null;
          });
    }
    return future.map(router);
  }
}
