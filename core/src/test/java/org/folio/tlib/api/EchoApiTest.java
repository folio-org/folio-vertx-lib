package org.folio.tlib.api;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;
import org.folio.tlib.postgres.TenantPgPool;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.folio.tlib.api.EchoApi.BODY_LIMIT;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class EchoApiTest {
  static Vertx vertx;
  static int port = 9230;

  @BeforeClass
  public static void beforeClass(TestContext context) {
    vertx = Vertx.vertx();
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
        .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testHealth() {
    RestAssured.given()
        .get("/admin/health")
        .then().statusCode(200)
        .contentType(ContentType.TEXT)
        .body(is("OK"));
  }

  @Test
  public void testEcho200() {
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
  public void testEcho413() {
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
  public void testTenant413() {
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
  public void testNoPath413() {
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
