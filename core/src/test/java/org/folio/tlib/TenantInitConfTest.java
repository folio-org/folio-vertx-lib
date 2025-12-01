package org.folio.tlib;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class TenantInitConfTest {

  @ParameterizedTest
  @CsvSource(textBlock = """
             foo, foo
             x_1_y, x_1_y
             123,
             %,
             ö,
             """)
  void tenant(String tenant, String expected) {
    var headers = MultiMap.caseInsensitiveMultiMap().add("X-OKAPI-TENANT", tenant);
    var tenantInitConf = new TenantInitConf(null, headers, null);
    if (expected == null) {
      assertThrows(IllegalArgumentException.class, tenantInitConf::tenant);
    } else {
      assertThat(tenantInitConf.tenant(), is(expected));
    }
  }

  @Test
  void moduleFromTo() {
    var json = new JsonObject().put("module_from", "mod-x-1.1.1").put("module_to", "2.2.2");
    var tenantInitConf = new TenantInitConf(null, null, json);
    assertThat(tenantInitConf.moduleFrom().toString(), is("1.1.1"));
    assertThat(tenantInitConf.moduleTo().toString(), is("2.2.2"));
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
             mod-x-1.2.3, 1.2.3
             3.4.5, 3.4.5
             ,
             '',
             """)
  void semVer(String moduleId, String semVer) {
    var tenantInitConf = new TenantInitConf(null, null, new JsonObject().put("module_from", moduleId));
    if (semVer == null) {
      assertThat(tenantInitConf.moduleFrom(), is(nullValue()));
    } else {
      assertThat(tenantInitConf.moduleFrom().toString(), is(semVer));
    }
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
             '{}, 0
             '{"parameters":[{},{}]}', 2
             """)
  void parameters(String json, int arraySize) {
    var tenantInitConf = new TenantInitConf(null, null, new JsonObject(json));
    assertThat(tenantInitConf.parameters().size(), is(arraySize));
  }
}
