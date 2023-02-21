package org.folio.tlib.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import org.folio.okapi.common.Config;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.api.HealthApi;
import org.folio.tlib.api.Tenant2Api;
import org.folio.tlib.example.service.BookService;
import org.folio.tlib.postgres.TenantPgPool;

public class MainVerticle extends AbstractVerticle {

  static final String MODULE = "mod-example";

  @Override
  public void start(Promise<Void> promise) {
    TenantPgPool.setModule(MODULE); // Postgres - schema separation

    // listening port
    final int port = Integer.parseInt(Config.getSysConf("http.port", "port", "8081", config()));

    BookService myApi = new BookService(); // your API, construct the way you like
    // routes for your stuff, tenant API and health
    RouterCreator[] routerCreators = {
        myApi,
        new Tenant2Api(myApi),
        new HealthApi(),
    };
    HttpServerOptions so = new HttpServerOptions()
        .setHandle100ContinueAutomatically(true);
    // combine all routes and start server
    RouterCreator.mountAll(vertx, routerCreators, MODULE)
        .compose(router ->
            vertx.createHttpServer(so)
                .requestHandler(router)
                .listen(port).mapEmpty())
        .<Void>mapEmpty()
        .onComplete(promise);
  }
}

