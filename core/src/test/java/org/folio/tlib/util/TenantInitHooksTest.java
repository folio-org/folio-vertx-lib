package org.folio.tlib.util;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.tlib.TenantInitHooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({VertxExtension.class})
class TenantInitHooksTest {

  class MyHooks implements TenantInitHooks {}

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void testDefault(Vertx vertx, VertxTestContext context) {
    MyHooks m = new MyHooks();
    m.postInit(null, null, null)
        .compose(x -> m.preInit(null, null, null))
        .onComplete(context.succeedingThenComplete());
  }
}
