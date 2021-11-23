package org.folio.tlib.util;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.tlib.TenantInitHooks;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TenantInitHooksTest {

  class MyHooks implements TenantInitHooks {}

  @Test
  public void testDefault(TestContext context) {
    MyHooks m = new MyHooks();
    m.postInit(null, null, null).onComplete(context.asyncAssertSuccess());
    m.preInit(null, null, null).onComplete(context.asyncAssertSuccess());
  }
}
