package org.folio.tlib;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.okapi.common.ModuleId;
import org.folio.okapi.common.SemVer;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.tlib.util.TenantUtil;

/**
 * Parameters of a POST /_/tenant call.
 */
public record TenantInitConf(Vertx vertx, MultiMap headers, JsonObject tenantAttributes) {
  public String okapiUrl() {
    return headers.get(XOkapiHeaders.URL);
  }

  public String tenant() {
    return TenantUtil.tenant(headers);
  }

  public String token() {
    return headers.get(XOkapiHeaders.TOKEN);
  }

  public SemVer moduleFrom() {
    return semVer(tenantAttributes.getString("module_from"));
  }

  public SemVer moduleTo() {
    return semVer(tenantAttributes.getString("module_to"));
  }

  /**
   * Returns the parameters array of tenantAttributes, or an empty array
   * if parameters is undefined or null.
   */
  public JsonArray parameters() {
    var parameters = tenantAttributes.getJsonArray("parameters");
    return parameters == null ? new JsonArray() : parameters;
  }

  private static SemVer semVer(String moduleId) {
    if (moduleId == null || moduleId.isEmpty()) {
      return null;
    }
    if (Character.isDigit(moduleId.charAt(0))) {
      return new SemVer(moduleId);
    }
    return new ModuleId(moduleId).getSemVer();
  }
}
