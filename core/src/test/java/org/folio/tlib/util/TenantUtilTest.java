package org.folio.tlib.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import io.vertx.ext.web.RoutingContext;
import java.util.List;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TenantUtilTest {

  @Test
  void utilityClass() {
    UtilityClassTester.assertUtilityClass(TenantUtil.class);
  }

  private String tenant(String tenant) {
    RoutingContext ctx = mock(RoutingContext.class, RETURNS_DEEP_STUBS);
    when(ctx.request().getHeader("X-Okapi-Tenant")).thenReturn(tenant);
    return TenantUtil.tenant(ctx);
  }

  @Test
  void valid() {
    List.of("a", "z1234567890", "du_15", "du_12_3", "_a", "a_b", "du_")
        .forEach(tenant -> assertThat(tenant(tenant), is(tenant)));
  }

  @Test
  void invalidNull() {
    Throwable t = Assertions.assertThrows(IllegalArgumentException.class, () -> tenant(null));
    assertThat(t.getMessage(), is("X-Okapi-Tenant header is missing"));
  }

  @Test
  void invalid() {
    List.of("", "1", "1abc").forEach(tenant -> {
      Throwable t = Assertions.assertThrows(IllegalArgumentException.class, () -> tenant(tenant));
      assertThat(t.getMessage(), containsString(" must match "));
    });
  }
}
