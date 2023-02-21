package org.folio.tlib;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.okapi.common.logging.FolioLoggingContext;

public interface RouterCreator {

  Future<Router> createRouter(Vertx vertx);

  /**
   * Create router for a list of RouterCreators.
   * @param vertx Vertx. handle
   * @param routerCreators list of router creators
   * @return async result with combined router.
   * @deprecated : use {{@link #mountAll(Vertx, RouterCreator[], String)}} and
   *     specify module.
   */
  @Deprecated
  static Future<Router> mountAll(Vertx vertx, RouterCreator [] routerCreators) {
    return mountAll(vertx, routerCreators, null);
  }

  /**
   * Create router for a list of RouterCreators.
   * @param vertx Vert.x handle
   * @param routerCreators list of router creators
   * @param module to be logged
   * @return async result with combined router.
   */
  static Future<Router> mountAll(Vertx vertx, RouterCreator [] routerCreators, String module) {
    Future<Void> future = Future.succeededFuture();
    Router router = Router.router(vertx);
    router.route().handler(ctx -> {
      FolioLoggingContext.put(FolioLoggingContext.MODULE_ID_LOGGING_VAR_NAME, module);
      MultiMap headers = ctx.request().headers();
      FolioLoggingContext.put(FolioLoggingContext.TENANT_ID_LOGGING_VAR_NAME,
          headers.get(XOkapiHeaders.TENANT));
      FolioLoggingContext.put(FolioLoggingContext.REQUEST_ID_LOGGING_VAR_NAME,
          headers.get(XOkapiHeaders.REQUEST_ID));
      FolioLoggingContext.put(FolioLoggingContext.USER_ID_LOGGING_VAR_NAME,
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
