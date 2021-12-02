package org.folio.tlib.example;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.tlib.postgres.testing.TenantPgPoolContainer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {
  static Vertx vertx;
  static int port;

  @ClassRule
  public static PostgreSQLContainer<?> postgresSQLContainer = TenantPgPoolContainer.create();

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException {
    port = 9230;
    vertx = Vertx.vertx();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://localhost:" + port;
    RestAssured.requestSpecification = new RequestSpecBuilder().build();

    DeploymentOptions deploymentOptions = new DeploymentOptions();
    deploymentOptions.setConfig(new JsonObject().put("port", Integer.toString(port)));
    vertx.deployVerticle(new MainVerticle(), deploymentOptions)
        .onComplete(context.asyncAssertSuccess());

  }

  @AfterClass
  public static void afterClass(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  void tenantOp(String tenant, JsonObject tenantAttributes, String expectedError) {
    ExtractableResponse<Response> response = RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .header("Content-Type", "application/json")
        .body(tenantAttributes.encode())
        .post("/_/tenant")
        .then()
        .extract();

    if (response.statusCode() == 204) {
      return;
    }
    assertThat(response.statusCode(), is(201));
    String location = response.header("Location");
    JsonObject tenantJob = new JsonObject(response.asString());
    assertThat(location, is("/_/tenant/" + tenantJob.getString("id")));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .get(location + "?wait=10000")
        .then().statusCode(200)
        .body("complete", is(true))
            .body("error", is(expectedError));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .delete(location)
        .then().statusCode(204);
  }

  @Test
  public void tenantInit(TestContext context) {
    String tenant = "testlib";
    tenantOp(tenant, new JsonObject()
        .put("module_to", "mod-mymodule-1.0.0")
            .put("parameters", new JsonArray()
                .add(new JsonObject().put("key", "loadSample").put("value", "true")))
        , null);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .get("/titles")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("titles", hasSize(2));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .queryParam("query", "cql.allRecords=true sortby title")
        .get("/titles")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("titles", hasSize(2))
        .body("titles[0].title", is("First title"))
        .body("titles[1].title", is("Second title"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .queryParam("query", "cql.allRecords=true sortby title/sort.descending")
        .get("/titles")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("titles", hasSize(2))
        .body("titles[0].title", is("Second title"))
        .body("titles[1].title", is("First title"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .queryParam("query", "title=first")
        .get("/titles")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("titles", hasSize(1))
        .body("titles[0].title", is("First title"));

    tenantOp(tenant, new JsonObject()
            .put("module_from", "mod-mymodule-1.0.0")
            .put("purge", true), null);

  }

}
