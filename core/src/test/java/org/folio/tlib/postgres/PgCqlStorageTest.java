package org.folio.tlib.postgres;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import java.util.List;
import java.util.UUID;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldFullText;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldText;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldUuid;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class PgCqlStorageTest {

  static Vertx vertx;

  @ClassRule
  public static PostgreSQLContainer<?> container = TenantPgPoolContainer.create();

  public static PgPool pgPool;

  static List<JsonObject> sample = List.of(
      new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("title", "On the road with Bob Dylan")
          .put("author", "Larry \"Ratso\" Sloman")
      ,
      new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("title", "Cry baby")
          .put("author",  "Garnet Mimms"));

  @BeforeClass
  public static void beforeClass(TestContext context) {
    vertx = Vertx.vertx();
    pgPool = PgPool.pool(vertx,
        new PgConnectOptions()
            .setPort(container.getFirstMappedPort())
            .setHost(container.getHost())
            .setDatabase(container.getDatabaseName())
            .setUser(container.getUsername())
            .setPassword(container.getPassword()),
        new PoolOptions().setMaxSize(2));
    pgPool.query("CREATE TABLE entries (id UUID, title TEXT, author TEXT)")
        .execute()
        .compose(x -> insertSample())
        .onComplete(context.asyncAssertSuccess());
  }

  static Future<Void> insertSample() {
    StringBuilder b = new StringBuilder("INSERT INTO entries (id, title, author) VALUES ");
    sample.forEach(o -> {
      b.append("('");
      b.append(o.getString("id"));
      b.append("', '");
      b.append(o.getString("title"));
      b.append("', '");
      b.append(o.getString("author"));
      b.append("'),");
    });
    b.append("('");
    b.append(UUID.randomUUID());
    b.append("', null, null);");
    return pgPool.query(b.toString())
        .execute()
        .mapEmpty();
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    pgPool.close();
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void checkCount(TestContext context) {
    pgPool.query("SELECT * FROM entries")
        .execute()
        .onComplete(context.asyncAssertSuccess(rowSet -> {
          assertThat(rowSet.rowCount(), is(sample.size() + 1));
        }));
  }

  Future<Void> test(String query, List<Integer> expected) {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("id", new PgCqlFieldUuid());
    pgCqlDefinition.addField("title", new PgCqlFieldFullText());
    pgCqlDefinition.addField("author", new PgCqlFieldText());
    PgCqlQuery parse = pgCqlDefinition.parse(query);
    return pgPool.query("SELECT * FROM entries WHERE " + parse.getWhereClause())
        .execute()
        .compose(rowSet -> {
          if (rowSet.rowCount() == expected.size()) {
            return Future.succeededFuture();
          }
          return Future.failedFuture("Query " + query
              + ". Expected size " + expected.size()
              + ", but got size " + rowSet.rowCount());
        });
  }

  @Test
  public void testIdEqualHit(TestContext context) {
    test("id=" + sample.get(0).getString("id"), List.of(0))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testIdEqualNoHit(TestContext context) {
    test("id=" + UUID.randomUUID(), List.of())
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testAuthorEqualHit(TestContext context) {
    test("author=\"Larry \\\"Ratso\\\" Sloman\"", List.of(0))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testAuthorEqualNoHit(TestContext context) {
    test("author=\"Larry \\\"Ratso\\\"\"", List.of())
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testAuthorEqualNoHit2(TestContext context) {
    test("author=\"Larry Ratso Sloman\"", List.of())
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit1(TestContext context) {
    test("title=\"road bob\"", List.of(0))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit2(TestContext context) {
    test("title=\"road bob\"", List.of(0))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit3(TestContext context) {
    test("title=\"with cry\"", List.of(0))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit4(TestContext context) {
    test("title=\"bob cry\"", List.of())
        .onComplete(context.asyncAssertSuccess());
  }

}
