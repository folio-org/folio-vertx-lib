package org.folio.tlib.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.client.WebClient;
import org.folio.okapi.common.Config;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.api.HealthApi;
import org.folio.tlib.api.Tenant2Api;
import org.folio.tlib.postgres.TenantPgPool;

public class MainVerticle extends AbstractVerticle {
  @Override
  public void start(Promise<Void> promise) {
    TenantPgPool.setModule("mod-mymodule"); // Postgres - schema separation

    final int port = Integer.parseInt( // listening port
        Config.getSysConf("http.port", "port", "8081", config()));

    MyApi myApi = new MyApi(); // your API, construct the way you like
    // routes for your stuff, tenant API and health
    RouterCreator[] routerCreators = {
        myApi,
        new Tenant2Api(myApi),
        new HealthApi(),
    };
    HttpServerOptions so = new HttpServerOptions()
        .setHandle100ContinueAutomatically(true);
    // combine all routes and start server
    RouterCreator.mountAll(vertx, WebClient.create(vertx), routerCreators)
        .compose(router ->
            vertx.createHttpServer(so)
                .requestHandler(router)
                .listen(port).mapEmpty())
        .<Void>mapEmpty()
        .onComplete(promise);
  }
}

