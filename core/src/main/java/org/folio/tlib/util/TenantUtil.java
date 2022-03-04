package org.folio.tlib.util;

import io.vertx.ext.web.RoutingContext;
import java.util.regex.Pattern;
import org.folio.okapi.common.XOkapiHeaders;

public final class TenantUtil {
  private static final String TENANT_PATTERN_STRING =  "^[_a-z][_a-z0-9]*$";
  private static final Pattern TENANT_PATTERN = Pattern.compile(TENANT_PATTERN_STRING);

  private TenantUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  /**
   * Return X-Okapi-Tenant header.
   * @throws IllegalArgumentException if header is missing or is invalid
   */
  public static String tenant(RoutingContext ctx) {
    String tenant = ctx.request().getHeader(XOkapiHeaders.TENANT);
    if (tenant == null) {
      throw new IllegalArgumentException(XOkapiHeaders.TENANT + " header is missing");
    }
    if (! TENANT_PATTERN.matcher(tenant).find()) {
      throw new IllegalArgumentException(
         XOkapiHeaders.TENANT + " header must match " + TENANT_PATTERN_STRING);
    }
    return tenant;
  }
}
