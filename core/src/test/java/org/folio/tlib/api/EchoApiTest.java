package org.folio.tlib.api;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.folio.tlib.api.EchoApi.BODY_LIMIT;
import static org.hamcrest.Matchers.is;

@Testcontainers
@ExtendWith({VertxExtension.class})
class EchoApiTest {
  static int port = 9230;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://localhost:" + port;
    RestAssured.requestSpecification = new RequestSpecBuilder().build();

    TenantInitHooks tenantInitHooks = new TenantInitHooks() {
      @Override
      public Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
        return TenantInitHooks.super.postInit(vertx, tenant, tenantAttributes);
      }
    };

    RouterCreator[] routerCreators = {
        new EchoApi(),
        new Tenant2Api(tenantInitHooks),
        new HealthApi(),
    };
    RouterCreator.mountAll(vertx, routerCreators)
        .compose(router -> {
          HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
          return vertx.createHttpServer(so)
              .requestHandler(router)
              .listen(port).mapEmpty();
        })
        .onComplete(context.succeedingThenComplete());
  }

  @Test
  void testHealth() {
    RestAssured.given()
        .get("/admin/health")
        .then().statusCode(200)
        .contentType(ContentType.TEXT)
        .body(is("OK"));
  }

  @Test
  void testEcho200() {
    String request = "x".repeat(BODY_LIMIT);
    RestAssured.given()
        .header("X-Okapi-Tenant", "tenant")
        .contentType(ContentType.TEXT)
        .body(request)
        .post("/echo")
        .then().statusCode(200)
        .contentType(ContentType.TEXT)
        .body(is(request));
  }

  @Test
  void testEcho413() {
    String request = "x".repeat(BODY_LIMIT+1); // one too many!
    RestAssured.given()
        .body(request)
        .post("/echo")
        .then().statusCode(413)
        // handled in EchoApi
        .contentType(ContentType.TEXT)
        .body(is("echo service status 413"));
  }

  @Test
  void testTenant413() {
    String request = "x".repeat(BODY_LIMIT + 1);
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(request)
        .post("/_/tenant")
        .then().statusCode(413)
        // handled in Tenant2Api which sets Content-Type
        .contentType(ContentType.TEXT)
        .body(is("Request Entity Too Large"));
  }

  @Test
  void testNoPath413() {
    String request = "x".repeat(BODY_LIMIT + 1);
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(request)
        .post("/nopath")
        .then().statusCode(413)
        // Unhandled exception in router. Vert.x creates response without Content-Type
        .body(is("Request Entity Too Large"));
  }

}
