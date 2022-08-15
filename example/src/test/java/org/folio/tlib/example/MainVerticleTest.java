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
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {
  static Vertx vertx;
  static int port;

  static final String TENANT = "testlib";

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
  public void testGetBooks(TestContext context) {
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/myapi/books")
        .then().statusCode(500)
        .contentType(ContentType.TEXT)
        .body(containsString("42P01"));

    tenantOp(TENANT, new JsonObject()
            .put("module_to", "mod-mymodule-1.0.0")
            .put("parameters", new JsonArray()
                .add(new JsonObject().put("key", "loadSample").put("value", "true")))
        , null);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/myapi/books")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("books", hasSize(2));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .queryParam("query", "cql.allRecords=true sortby title")
        .get("/myapi/books")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("books", hasSize(2))
        .body("books[0].title", is("First title"))
        .body("books[0].indexTitle", is("first title"))
        .body("books[1].title", is("Second title"))
        .body("books[1].indexTitle", is("second title"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .queryParam("query", "cql.allRecords=true sortby title/sort.descending")
        .get("/myapi/books")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("books", hasSize(2))
        .body("books[0].title", is("Second title"))
        .body("books[1].title", is("First title"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .queryParam("query", "title=first")
        .get("/myapi/books")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("books", hasSize(1))
        .body("books[0].title", is("First title"));

    tenantOp(TENANT, new JsonObject()
        .put("module_from", "mod-mymodule-1.0.0")
        .put("purge", true), null);

  }

  @Test
  public void testPostBook(TestContext context) {
    Book a = new Book();
    a.setTitle("art of computer");
    a.setId(UUID.randomUUID());
    a.setIndexTitle("art computer");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .contentType(ContentType.JSON)
        .body(JsonObject.mapFrom(a).encode())
        .post("/myapi/books")
        .then().statusCode(500)
        .body(containsString("42P01"));

    tenantOp(TENANT, new JsonObject()
            .put("module_to", "mod-mymodule-1.0.0")
        , null);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/myapi/books")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("books", hasSize(0));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .contentType(ContentType.JSON)
        .body(JsonObject.mapFrom(a).encode())
        .post("/myapi/books")
        .then().statusCode(204);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/myapi/books")
        .then().statusCode(200)
        .contentType(ContentType.JSON)
        .body("books[0].id", is(a.getId().toString()))
        .body("books[0].title", is(a.getTitle()))
        .body("books[0].indexTitle", is(a.getIndexTitle()));

    tenantOp(TENANT, new JsonObject()
        .put("module_from", "mod-mymodule-1.0.0")
        .put("purge", true), null);
  }

}
