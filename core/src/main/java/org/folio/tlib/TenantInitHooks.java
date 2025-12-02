package org.folio.tlib;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.tlib.api.Tenant2Api;

/**
 * Hooks called when Okapi enables a module for a tenant.
 *
 * <p>The preInit job should be "fast" and is a way for the module to check
 * if the operation can be started ("pre-check"). The postInit should perform the actual migration.
 *
 * <p>The {@link Tenant2Api} implementation deals with purge (removes schema with cascade).
 * Your implementation should only consider upgrade/downgrade. On purge, preInit is called,
 * but postInit is not.
 *
 * <p>Overwrite at most one of the postInit methods and at most one of the preInit methods.
 */
public interface TenantInitHooks {
  /**
   * If overwriting {@link #postInit(TenantInitConf)} then
   * {@link #postInit(Vertx, String, JsonObject)} is not called.
   */
  default Future<Void> postInit(TenantInitConf tenantInitConf) {
    return postInit(tenantInitConf.vertx(), tenantInitConf.tenant(),
        tenantInitConf.tenantAttributes());
  }

  default Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    return Future.succeededFuture();
  }

  /**
   * If overwriting {@link #preInit(TenantInitConf)} then
   * {@link #preInit(Vertx, String, JsonObject)} is not called.
   */
  default Future<Void> preInit(TenantInitConf tenantInitConf) {
    return preInit(tenantInitConf.vertx(), tenantInitConf.tenant(),
        tenantInitConf.tenantAttributes());
  }

  default Future<Void> preInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    return Future.succeededFuture();
  }

}
