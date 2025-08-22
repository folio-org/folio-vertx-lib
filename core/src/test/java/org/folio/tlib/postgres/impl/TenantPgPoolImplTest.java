package org.folio.tlib.postgres.impl;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({VertxExtension.class})
class TenantPgPoolImplTest {

  @BeforeEach
  void before() {
    TenantPgPoolImpl.host = null;
    TenantPgPoolImpl.port = null;
    TenantPgPoolImpl.database = null;
    TenantPgPoolImpl.user = null;
    TenantPgPoolImpl.password = null;
    TenantPgPoolImpl.maxPoolSize = null;
    TenantPgPoolImpl.reconnectAttempts = null;
    TenantPgPoolImpl.reconnectInterval = null;
    TenantPgPoolImpl.maxLifetime = null;
    TenantPgPoolImpl.serverPem = null;
    TenantPgPoolImpl.setModule("mod-a");
  }

  @AfterEach
  void after(Vertx vertx, VertxTestContext context) {
    TenantPgPoolImpl.closeAll()
        .onComplete(context.succeedingThenComplete());
  }

  @Test
  void testDefault(Vertx vertx, VertxTestContext context) {
    TenantPgPoolImpl.setModule(null);
    Assertions.assertNull(TenantPgPoolImpl.module);
    TenantPgPoolImpl.setModule("a-b.c");
    Assertions.assertEquals("a_b_c", TenantPgPoolImpl.module);
    TenantPgPoolImpl pool = TenantPgPoolImpl.tenantPgPool(vertx, "diku");
    Assertions.assertEquals("diku_a_b_c", pool.getSchema());
    Assertions.assertEquals(30000, pool.poolOptions.getMaxLifetime());
    context.completeNow();
  }

  @Test
  void testAll(Vertx vertx, VertxTestContext context) {
    PgConnectOptions options = new PgConnectOptions();
    TenantPgPoolImpl.setDefaultConnectOptions(options);
    TenantPgPoolImpl.setModule("mod-a");
    TenantPgPoolImpl.host = "host_val";
    TenantPgPoolImpl.port = "9765";
    TenantPgPoolImpl.database = "database_val";
    TenantPgPoolImpl.user = "user_val";
    TenantPgPoolImpl.password = "password_val";
    TenantPgPoolImpl.maxPoolSize = "5";
    TenantPgPoolImpl.maxLifetime = "6";
    TenantPgPoolImpl.reconnectAttempts = "3";
    TenantPgPoolImpl.reconnectInterval = "2";
    TenantPgPoolImpl pool = TenantPgPoolImpl.tenantPgPool(vertx, "diku");
    Assertions.assertEquals("diku_mod_a", pool.getSchema());
    Assertions.assertEquals("host_val", options.getHost());
    Assertions.assertEquals(9765, options.getPort());
    Assertions.assertEquals("database_val", options.getDatabase());
    Assertions.assertEquals("user_val", options.getUser());
    Assertions.assertEquals("password_val", options.getPassword());
    Assertions.assertEquals(5, pool.poolOptions.getMaxSize());
    Assertions.assertEquals(6, pool.poolOptions.getMaxLifetime());
    Assertions.assertEquals(3, options.getReconnectAttempts());
    Assertions.assertEquals(2, options.getReconnectInterval());
    context.completeNow();
  }

  @Test
  void testUserDefined() {
    PgConnectOptions userDefined = new PgConnectOptions();
    userDefined.setHost("localhost2");
    TenantPgPoolImpl.setDefaultConnectOptions(userDefined);
    Assertions.assertEquals(userDefined, TenantPgPoolImpl.pgConnectOptions);
    Assertions.assertEquals("localhost2", userDefined.getHost());
    userDefined = new PgConnectOptions();
    TenantPgPoolImpl.setDefaultConnectOptions(userDefined);
    Assertions.assertEquals(userDefined, TenantPgPoolImpl.pgConnectOptions);
    Assertions.assertNotEquals("localhost2", userDefined.getHost());
  }

  @Test
  void testPoolReuse(Vertx vertx, VertxTestContext context) {
    TenantPgPoolImpl pool1 = TenantPgPoolImpl.tenantPgPool(vertx, "diku1");
    Assertions.assertEquals("diku1_mod_a", pool1.getSchema());
    TenantPgPoolImpl pool2 = TenantPgPoolImpl.tenantPgPool(vertx, "diku2");
    Assertions.assertEquals("diku2_mod_a", pool2.getSchema());
    Assertions.assertNotEquals(pool1, pool2);
    Assertions.assertEquals(pool1.pgPool, pool2.pgPool);
    context.completeNow();
  }

}
