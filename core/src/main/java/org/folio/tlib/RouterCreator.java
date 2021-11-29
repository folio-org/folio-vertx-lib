package org.folio.tlib;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;

public interface RouterCreator {

  Future<Router> createRouter(Vertx vertx, WebClient webClient);

  /**
   * Create router for a list of RouterCreators.
   * @param vertx Vertx. handle
   * @param webClient WebClient to use
   * @param routerCreators list of router creators
   * @return async result with combined router.
   */
  static Future<Router> mountAll(Vertx vertx, WebClient webClient,
      RouterCreator [] routerCreators) {
    Router router = Router.router(vertx);
    Future<Void> future = Future.succeededFuture();
    for (RouterCreator routerCreator : routerCreators) {
      future = future.compose(x -> routerCreator.createRouter(vertx, webClient))
          .map(subRouter -> {
            router.mountSubRouter("/", subRouter);
            return null;
          });
    }
    return future.map(router);
  }
}
