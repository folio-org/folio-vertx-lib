package org.folio.tlib;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.logging.FolioLocal;
import org.folio.okapi.common.logging.FolioLoggingContext;

/**
 * Creates a router that supports Okapi HTTP headers.
 */
public interface RouterCreator {

  Future<Router> createRouter(Vertx vertx);

  /**
   * Create router for a list of RouterCreators.
   *
   * @param vertx Vertx. handle
   * @param routerCreators list of router creators
   * @return async result with combined router.
   * @deprecated : use {{@link #mountAll(Vertx, RouterCreator[], String)}} and
   *     specify module.
   */
  @Deprecated(since = "3.1.0")
  static Future<Router> mountAll(Vertx vertx, RouterCreator [] routerCreators) {
    return mountAll(vertx, routerCreators, null);
  }

  /**
   * Create router for a list of RouterCreators.
   *
   * @param vertx Vert.x handle
   * @param routerCreators list of router creators
   * @param module to be logged
   * @return async result with combined router.
   */
  static Future<Router> mountAll(Vertx vertx, RouterCreator [] routerCreators, String module) {
    Future<Void> future = Future.succeededFuture();
    Router router = Router.router(vertx);
    router.route().handler(ctx -> {
      FolioLoggingContext.put(FolioLocal.MODULE_ID, module);
      MultiMap headers = ctx.request().headers();
      FolioLoggingContext.put(FolioLocal.TENANT_ID,
          headers.get(XOkapiHeaders.TENANT));
      FolioLoggingContext.put(FolioLocal.REQUEST_ID,
          headers.get(XOkapiHeaders.REQUEST_ID));
      FolioLoggingContext.put(FolioLocal.USER_ID,
          headers.get(XOkapiHeaders.USER_ID));
      ctx.next();
    });
    for (RouterCreator routerCreator : routerCreators) {
      future = future.compose(x -> routerCreator.createRouter(vertx))
          .map(subRouter -> {
            router.route("/*").subRouter(subRouter);
            return null;
          });
    }
    return future.map(router);
  }
}
