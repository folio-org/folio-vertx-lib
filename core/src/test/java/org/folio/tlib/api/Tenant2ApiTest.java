package org.folio.tlib.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitConf;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.tlib.postgres.TenantPgPoolContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Timeout(10000)
@Testcontainers
@ExtendWith({VertxExtension.class})
class Tenant2ApiTest {
  static int port = 9230;

  private static int getFreePort() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      serverSocket.setReuseAddress(true);
      return serverSocket.getLocalPort();
    }
  }

  @Container
  static PostgreSQLContainer<?> postgresSQLContainer = TenantPgPoolContainer.create();

  static class TestInitHooks implements org.folio.tlib.TenantInitHooks {

    Promise<Void> preInitPromise;
    Promise<Void> postInitPromise;

    @Override
    public Future<Void> preInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
      if (preInitPromise == null) {
        return Future.succeededFuture();
      }
      return preInitPromise.future();
    }

    @Override
    public Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
      if (postInitPromise == null) {
        return Future.succeededFuture();
      }
      return postInitPromise.future();
    }
  }

  static TestInitHooks hooks = new TestInitHooks();

  static class TestInitHooks2 implements org.folio.tlib.TenantInitHooks {

    TenantInitConf preInitConf;
    TenantInitConf postInitConf;

    @Override
    public Future<Void> preInit(TenantInitConf tenantInitConf) {
      preInitConf = tenantInitConf;
      return Future.succeededFuture();
    }

    @Override
    public Future<Void> postInit(TenantInitConf tenantInitConf) {
      postInitConf = tenantInitConf;
      return Future.succeededFuture();
    }
  }

  static TestInitHooks2 hooks2 = new TestInitHooks2();

  private static PgConnectOptions initialPgConnectOptions;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    TenantPgPool.setModule("mod-tenant");
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://localhost:" + port;
    RestAssured.requestSpecification = new RequestSpecBuilder().build();

    initialPgConnectOptions = TenantPgPool.getDefaultConnectOptions();

    RouterCreator [] routerCreators = {
        new Tenant2Api(hooks)
    };
    RouterCreator.mountAll(vertx, routerCreators, "mod-tenant")
        .compose(router -> {
          HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
          return vertx.createHttpServer(so)
              .requestHandler(router)
              .listen(port).mapEmpty();
        })
        .onComplete(context.succeedingThenComplete());
  }

  @AfterAll
  static void afterAll(VertxTestContext context) {
    TenantPgPool.closeAll()
        .onComplete(context.succeedingThenComplete());
  }

  @AfterEach
  void setDefaultConnectOptions() {
    TenantPgPool.setDefaultConnectOptions(initialPgConnectOptions);
  }

  @BeforeEach
  void setup() {
    hooks.preInitPromise = null;
    hooks.postInitPromise = null;
  }

  @Test
  void testPostTenantBadTenant1() {
    String tenant = "test'lib";
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("The value of header parameter X-Okapi-Tenant is invalid. Reason: String does not match pattern"));
  }

  @Test
  void testPostTenantBadTenant2() {
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("The related request / response does not contain the required header parameter X-Okapi-Tenant"));
  }

  @Test
  void testPostTenantBadPort() throws IOException {
    Assumptions.assumeTrue(System.getenv("DB_HOST") == null);
    Assumptions.assumeTrue(System.getenv("DB_PORT") == null);

    String tenant = "testlib";
    PgConnectOptions bad = new PgConnectOptions(initialPgConnectOptions);
    bad.setPort(getFreePort());
    TenantPgPool.setDefaultConnectOptions(bad);
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("DB_PORT="));
  }

  @Test
  void testPostTenantBadDatabase() {
    Assumptions.assumeTrue(System.getenv("DB_DATABASE") == null);

    String tenant = "testlib";
    PgConnectOptions bad = new PgConnectOptions(initialPgConnectOptions);
    bad.setDatabase(bad.getDatabase() + "_foo");
    TenantPgPool.setDefaultConnectOptions(bad);
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(500)
        .contentType(ContentType.TEXT)
        .body(containsString("database"));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .get("/_/tenant/" + UUID.randomUUID())
        .then().statusCode(500)
        .contentType(ContentType.TEXT)
        .body(containsString("database"));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .delete("/_/tenant/" + UUID.randomUUID())
        .then().statusCode(500)
        .contentType(ContentType.TEXT)
        .body(containsString("database"));
  }

  @Test
  void testPostTenantOK() {
    String tenant = "testlib";

    // init
    tenantOp(tenant, new JsonObject()
        .put("module_to", "mod-eusage-reports-1.0.0")
    );

    // upgrade
    tenantOp(tenant, new JsonObject()
        .put("module_from", "mod-eusage-reports-1.0.0")
        .put("module_to", "mod-eusage-reports-1.0.1")
    );

    // disable
    tenantOp(tenant, new JsonObject()
        .put("module_from", "mod-eusage-reports-1.0.1")
    );

    // purge
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body(new JsonObject()
            .put("module_from", "mod-eusage-reports-1.0.1")
            .put("purge", true)
            .encode())
        .post("/_/tenant")
        .then().statusCode(204);

    // 2nd purge
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body(new JsonObject()
            .put("module_from", "mod-eusage-reports-1.0.1")
            .put("purge", true)
            .encode())
        .post("/_/tenant")
        .then().statusCode(204);
  }

  @Test
  void testPostTenantPreInitFail() {
    String tenant = "testlib";
    hooks.preInitPromise = Promise.promise();
    hooks.preInitPromise.fail("pre init failure");
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(500)
        .contentType(ContentType.TEXT)
        .body(is("pre init failure"));
  }

  private void tenantOp(String tenant, JsonObject body) {
    hooks.postInitPromise = Promise.promise(); // not completed yet

    ExtractableResponse<Response> response = RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body(body.encode())
        .post("/_/tenant")
        .then().statusCode(201)
        .contentType(ContentType.JSON)
        .body("tenant", is(tenant))
        .body("error", is(nullValue()))
        .extract();

    String location = response.header("Location");
    String id = response.path("id");
    assertThat(location, is("/_/tenant/" + id));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .get(location)
        .then().statusCode(200)
        .body("complete", is(false))
        .body("error", is(nullValue()));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .get(location + "?wait=1000")  // wait for up to 1000 milliseconds
    .then()
        .statusCode(200)
        .body("complete", is(false))
        .body("error", is(nullValue()))
        .time(greaterThan(500L /* ms */))
        .time(lessThan(1500L /* ms */));

    Vertx.vertx().setTimer(1000 /* ms */, timerFired -> hooks.postInitPromise.tryComplete());

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .get(location + "?wait=5000")
    .then()
        .statusCode(200)
        .body("complete", is(true))
        .body("error", is(nullValue()))
        .time(greaterThan(500L /* ms */))
        .time(lessThan(1500L /* ms */));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .delete(location)
        .then().statusCode(204);

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .delete(location)
        .then().statusCode(404);
  }

  @Test
  void testPostTenantPostInitFail() {
    hooks.postInitPromise = Promise.promise();
    hooks.postInitPromise.fail("post init failure");
    String tenant = "testlib";
    ExtractableResponse<Response> response = RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(201)
        .contentType(ContentType.JSON)
        .body("tenant", is(tenant))
        .extract();

    String location = response.header("Location");
    String id = response.path("id");
    assertThat(location,  is("/_/tenant/" + id));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .get(location)
        .then().statusCode(200)
        .body("complete", is(true))
        .body("error", is("post init failure"));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .delete(location)
        .then().statusCode(204);

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\", \"purge\":true}")
        .post("/_/tenant")
        .then().statusCode(204);
  }

  @Test
  void testPostTenantPostInitFailNull() {
    String tenant = "testlib";
    hooks.postInitPromise = Promise.promise();
    hooks.postInitPromise.fail((String) null);
    ExtractableResponse<Response> response = RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(201)
        .contentType(ContentType.JSON)
        .body("tenant", is(tenant))
        .extract();

    String location = response.header("Location");
    String id = response.path("id");
    assertThat(location,  is("/_/tenant/" + id));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .get(location + "?wait=1")
        .then().statusCode(200)
        .body("complete", is(true))
        .body("error", is("io.vertx.core.impl.NoStackTraceThrowable"));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .delete(location)
        .then().statusCode(204);

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\", \"purge\":true}")
        .post("/_/tenant")
        .then().statusCode(204);
  }

  @Test
  void testPostMissingTenant() {
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(400)
        .contentType(ContentType.TEXT);
  }

  @Test
  void testGetMissingTenant(){
    String id = UUID.randomUUID().toString();
    RestAssured.given()
        .get("/_/tenant/" + id)
        .then().statusCode(400)
        .contentType(ContentType.TEXT);
  }

  @Test
  void testGetBadId(){
    String id = "1234";
    RestAssured.given()
        .header("X-Okapi-Tenant", "testlib")
        .get("/_/tenant/" + id)
        .then().statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("he value of path parameter id is invalid. Reason: String does not match format \"uuid\""));
  }

  @Test
  void testDeleteMissingTenant(){
    String id = UUID.randomUUID().toString();
    RestAssured.given()
        .delete("/_/tenant/" + id)
        .then().statusCode(400)
        .contentType(ContentType.TEXT);
  }

  @Test
  void testDeleteBadId(){
    String id = "1234";
    RestAssured.given()
        .header("X-Okapi-Tenant", "testlib")
        .delete("/_/tenant/" + id)
        .then().statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("The value of path parameter id is invalid. Reason: String does not match format \"uuid\""));
  }

  @Test
  void testPostTenantBadJson() {
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"")
        .post("/_/tenant")
        .then().statusCode(400);
  }

  @Test
  void testPostTenantBadType() {
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : true}")
        .post("/_/tenant")
        .then().statusCode(400);
  }

  @Test
  void testPostTenantAdditional() {
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\", \"extra\":true}")
        .post("/_/tenant")
        .then().statusCode(400);
  }

  @Test
  void testInitHooks2(Vertx vertx, VertxTestContext vtc) {
    hooks2.preInitConf = null;
    hooks2.postInitConf = null;
    var tenant2Api = new Tenant2Api(hooks2);
    tenant2Api.createRouter(vertx)
    .compose(router -> vertx.createHttpServer().requestHandler(router).listen(0))
    .compose(httpServer -> {
      return vertx.createHttpClient()
          .request(HttpMethod.POST, httpServer.actualPort(), "localhost", "/_/tenant");
    })
    .compose(httpClientRequest -> {
      return httpClientRequest
          .putHeader("x-okapi-tenant", "foo")
          .putHeader("x-okapi-url", "https://example.com")
          .putHeader("X-OKAPI-TOKEN", "Rumpelstiltskin")
          .putHeader("Content-Type", "application/json")
          .send("""
                { "module_to": "mod-x-1.0.0",
                  "parameters": [ { "key": "x", "value": "y" } ]
                }
                """);
    })
    .onComplete(vtc.succeeding(httpClientResponse -> {
      assertThat(httpClientResponse.statusCode(), is(201));
      assertThat(hooks2.preInitConf, is(hooks2.postInitConf));
      assertThat(hooks2.postInitConf.tenant(), is("foo"));
      assertThat(hooks2.postInitConf.token(), is("Rumpelstiltskin"));
      assertThat(hooks2.postInitConf.okapiUrl(), is("https://example.com"));
      assertThat(hooks2.postInitConf.tenantAttributes()
          .getJsonArray("parameters").getJsonObject(0).getString("key"), is("x"));
      vtc.completeNow();
    }));
  }
}
