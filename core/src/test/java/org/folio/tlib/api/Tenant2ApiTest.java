package org.folio.tlib.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.pgclient.PgConnectOptions;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.tlib.postgres.TenantPgPoolContainer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.testcontainers.containers.PostgreSQLContainer;

@RunWith(VertxUnitRunner.class)
public class Tenant2ApiTest {
  private final static Logger log = LogManager.getLogger(Tenant2ApiTest.class);

  static Vertx vertx;
  static int port = 9230;

  private static int getFreePort() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      serverSocket.setReuseAddress(true);
      return serverSocket.getLocalPort();
    }
  }

  @Rule
  public Timeout timeout = Timeout.seconds(10);

  @ClassRule
  public static PostgreSQLContainer<?> postgresSQLContainer = TenantPgPoolContainer.create();

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
  private static PgConnectOptions initialPgConnectOptions;

  @BeforeClass
  public static void beforeClass(TestContext context) {
    TenantPgPool.setModule("mod-tenant");
    vertx = Vertx.vertx();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://localhost:" + port;
    RestAssured.requestSpecification = new RequestSpecBuilder().build();

    initialPgConnectOptions = TenantPgPool.getDefaultConnectOptions();

    RouterCreator [] routerCreators = {
        new Tenant2Api(hooks),
        new HealthApi(),
    };
    RouterCreator.mountAll(vertx, routerCreators)
        .compose(router -> {
          HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
          return vertx.createHttpServer(so)
              .requestHandler(router)
              .listen(port).mapEmpty();
        })
        .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @After
  public void setDefaultConnectOptions() {
    TenantPgPool.setDefaultConnectOptions(initialPgConnectOptions);
  }

  @Before
  public void setup() {
    hooks.preInitPromise = null;
    hooks.postInitPromise = null;
  }

  @Test
  public void testHealth() {
    RestAssured.given()
        .get("/admin/health")
        .then().statusCode(200)
        .header("Content-Type", is("text/plain"))
        .body(is("OK"));
  }

  @Test
  public void testPostTenantBadTenant1() {
    String tenant = "test'lib";
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(400)
        .header("Content-Type", is("text/plain"))
        .body(containsString("X-Okapi-Tenant header must match"));
  }

  @Test
  public void testPostTenantBadTenant2() {
    String tenant = "test\"lib";
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(400)
        .header("Content-Type", is("text/plain"))
        .body(containsString("X-Okapi-Tenant header must match"));
  }

  @Test
  public void testPostTenantBadPort() throws IOException {
    Assume.assumeThat(System.getenv("DB_HOST"), is(nullValue()));
    Assume.assumeThat(System.getenv("DB_PORT"), is(nullValue()));

    String tenant = "testlib";
    PgConnectOptions bad = new PgConnectOptions(initialPgConnectOptions);
    bad.setPort(getFreePort());
    TenantPgPool.setDefaultConnectOptions(bad);
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(400)
        .header("Content-Type", is("text/plain"))
        .body(containsString("DB_PORT="));
  }

  @Test
  public void testPostTenantBadDatabase() {
    Assume.assumeThat(System.getenv("DB_DATABASE"), is(nullValue()));

    String tenant = "testlib";
    PgConnectOptions bad = new PgConnectOptions(initialPgConnectOptions);
    bad.setDatabase(bad.getDatabase() + "_foo");
    TenantPgPool.setDefaultConnectOptions(bad);
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(500)
        .header("Content-Type", is("text/plain"))
        .body(containsString("database"));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .get("/_/tenant/" + UUID.randomUUID().toString())
        .then().statusCode(500)
        .header("Content-Type", is("text/plain"))
        .body(containsString("database"));

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .delete("/_/tenant/" + UUID.randomUUID().toString())
        .then().statusCode(500)
        .header("Content-Type", is("text/plain"))
        .body(containsString("database"));
  }

  @Test
  public void testPostTenantOK() {
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
        .header("Content-Type", "application/json")
        .body(new JsonObject()
            .put("module_from", "mod-eusage-reports-1.0.1")
            .put("purge", true)
            .encode())
        .post("/_/tenant")
        .then().statusCode(204);

    // 2nd purge
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body(new JsonObject()
            .put("module_from", "mod-eusage-reports-1.0.1")
            .put("purge", true)
            .encode())
        .post("/_/tenant")
        .then().statusCode(204);
  }

  @Test
  public void testPostTenantPreInitFail() {
    String tenant = "testlib";
    hooks.preInitPromise = Promise.promise();
    hooks.preInitPromise.fail("pre init failure");
    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(500)
        .header("Content-Type", is("text/plain"))
        .body(is("pre init failure"));
  }

  private void tenantOp(String tenant, JsonObject body) {
    hooks.postInitPromise = Promise.promise(); // not completed yet

    ExtractableResponse<Response> response = RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body(body.encode())
        .post("/_/tenant")
        .then().statusCode(201)
        .header("Content-Type", is("application/json"))
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
        .get(location + "?wait=1")  // wait for up to 1 second
    .then()
        .statusCode(200)
        .body("complete", is(false))
        .body("error", is(nullValue()))
        .time(greaterThan(500L /* ms */))
        .time(lessThan(1500L /* ms */));

    vertx.setTimer(1000 /* ms */, timerFired -> hooks.postInitPromise.tryComplete());

    RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .get(location + "?wait=5")
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
  public void testPostTenantPostInitFail() {
    hooks.postInitPromise = Promise.promise();
    hooks.postInitPromise.fail("post init failure");
    String tenant = "testlib";
    ExtractableResponse<Response> response = RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(201)
        .header("Content-Type", is("application/json"))
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
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\", \"purge\":true}")
        .post("/_/tenant")
        .then().statusCode(204);
  }

  @Test
  public void testPostTenantPostInitFailNull() {
    String tenant = "testlib";
    hooks.postInitPromise = Promise.promise();
    hooks.postInitPromise.fail((String) null);
    ExtractableResponse<Response> response = RestAssured.given()
        .header("X-Okapi-Tenant", tenant)
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(201)
        .header("Content-Type", is("application/json"))
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
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\", \"purge\":true}")
        .post("/_/tenant")
        .then().statusCode(204);
  }

  @Test
  public void testPostMissingTenant() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"}")
        .post("/_/tenant")
        .then().statusCode(400)
        .header("Content-Type", is("text/plain"));
  }

  @Test
  public void testGetMissingTenant(){
    String id = UUID.randomUUID().toString();
    RestAssured.given()
        .get("/_/tenant/" + id)
        .then().statusCode(400)
        .header("Content-Type", is("text/plain"));
  }

  @Test
  public void testGetBadId(){
    String id = "1234";
    RestAssured.given()
        .header("X-Okapi-Tenant", "testlib")
        .get("/_/tenant/" + id)
        .then().statusCode(400)
        .header("Content-Type", is("text/plain"))
        .body(containsString("Validation error for parameter id in location"));
  }

  @Test
  public void testDeleteMissingTenant(){
    String id = UUID.randomUUID().toString();
    RestAssured.given()
        .delete("/_/tenant/" + id)
        .then().statusCode(400)
        .header("Content-Type", is("text/plain"));
  }

  @Test
  public void testDeleteBadId(){
    String id = "1234";
    RestAssured.given()
        .header("X-Okapi-Tenant", "testlib")
        .delete("/_/tenant/" + id)
        .then().statusCode(400)
        .header("Content-Type", is("text/plain"))
        .body(containsString("Validation error for parameter id in location"));
  }

  @Test
  public void testPostTenantBadJson() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\"")
        .post("/_/tenant")
        .then().statusCode(400);
  }

  @Test
  public void testPostTenantBadType() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : true}")
        .post("/_/tenant")
        .then().statusCode(400);
  }

  @Test
  public void testPostTenantAdditional() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .body("{\"module_to\" : \"mod-eusage-reports-1.0.0\", \"extra\":true}")
        .post("/_/tenant")
        .then().statusCode(400);
  }

}
