package org.folio.tlib.postgres;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldAlwaysMatches;
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
          .put("author",  "Garnet Mimms")
      ,
      new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("title", "only title")
  );

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

  static String getValue(String elem) {
    if (elem == null) {
      return "NULL";
    }
    return "'" + elem + "'";
  }
  static Future<Void> insertSample() {
    StringBuilder b = new StringBuilder("INSERT INTO entries (id, title, author) VALUES ");
    sample.forEach(o -> {
      b.append("(");
      b.append(getValue(o.getString("id")));
      b.append(", ");
      b.append(getValue(o.getString("title")));
      b.append(", ");
      b.append(getValue(o.getString("author")));
      b.append("),");
    });
    b.append("('");
    b.append(UUID.randomUUID());
    b.append("', NULL, NULL);");
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
        .onComplete(context.asyncAssertSuccess(rowSet -> assertThat(rowSet.rowCount(), is(sample.size() + 1))));
  }

  Future<Void> test(String query, List<Integer> expected) {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("cql.allRecords", new PgCqlFieldAlwaysMatches());
    pgCqlDefinition.addField("id", new PgCqlFieldUuid());
    pgCqlDefinition.addField("title", new PgCqlFieldText().withFullText());
    pgCqlDefinition.addField("author", new PgCqlFieldText().withLikeOps());
    PgCqlQuery parse = pgCqlDefinition.parse(query);
    String where = parse.getWhereClause() == null ? ""
        : "WHERE " + parse.getWhereClause();
    return pgPool.query("SELECT * FROM entries " + where)
        .execute()
        .compose(rowSet -> {
          Set<Integer> got = new HashSet<>();
          rowSet.forEach(row -> {
            String id = row.getUUID("id").toString();
            Iterator<JsonObject> iterator = sample.iterator();
            int off;
            for (off = 0; iterator.hasNext(); off++) {
              if (iterator.next().getString("id").equals(id)) {
                break;
              }
            }
            got.add(off);
          });
          if (got.size() == expected.size() && got.containsAll(expected)) {
            return Future.succeededFuture();
          }
          return Future.failedFuture("Query " + query
              + ". Expected entries ["
              + expected.stream().map(x -> Integer.toString(x)).collect(Collectors.joining(","))
              + "], but got ["
              + got.stream().map(x -> Integer.toString(x)).collect(Collectors.joining(","))
              + "]");
        });
  }

  @Test
  public void testAllRecords(TestContext context) {
    test("cql.allRecords=1", List.of(0, 1, 2, 3))
        .onComplete(context.asyncAssertSuccess());
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
    test("title=\"road with bob\"", List.of(0))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit2(TestContext context) {
    test("title=\"Road the bob\"", List.of(0)) // "the" and "with" are both stop words.
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit3(TestContext context) {
    test("title=\"bob the road\"", List.of())
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit4(TestContext context) {
    test("title all \"bob the road\"", List.of(0))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit5(TestContext context) {
    test("title=\"with cry\"", List.of(1)) // "with" a stop word
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testTitleHit6(TestContext context) {
    test("title=\"bob cry\"", List.of())
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testAuthorEqEmpty(TestContext context) {
    test("author=\"\"", List.of(0, 1))
        .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testAuthorMask(TestContext context) {
    test("author = Garnet Mi?ms", List.of(1))
        .onComplete(context.asyncAssertSuccess());
  }
}
