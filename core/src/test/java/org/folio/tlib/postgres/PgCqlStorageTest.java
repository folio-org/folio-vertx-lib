package org.folio.tlib.postgres;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.folio.tlib.postgres.cqlfield.PgCqlFieldAlwaysMatches;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldText;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldUuid;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;

import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testcontainers
@ExtendWith({VertxExtension.class})
public class PgCqlStorageTest {

  @Container
  static final PostgreSQLContainer<?> container = TenantPgPoolContainer.create();

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

  @BeforeAll
  static void beforeClass(Vertx vertx, VertxTestContext context) {
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
        .onComplete(context.succeedingThenComplete());
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

  @AfterAll
  static void afterClass(Vertx vertx, VertxTestContext context) {
    pgPool.close().onComplete(context.succeedingThenComplete());
  }

  @Test
  private void checkCount(Vertx vertx, VertxTestContext context) {
    pgPool.query("SELECT * FROM entries")
        .execute()
        .onComplete(context.succeeding(rowSet -> assertThat(rowSet.rowCount(), is(sample.size() + 1))));
  }

  private Future<Void> test(String query, List<Integer> expected) {
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

  static Stream<Arguments> cqlQueries() {
    return Stream.of(
        Arguments.of("cql.allRecords=1", List.of(0, 1, 2, 3)),
        Arguments.of("id=" + sample.get(0).getString("id"), List.of(0)),
        Arguments.of("id=" + UUID.randomUUID(), List.of()),
        Arguments.of("author=\"Larry \\\"Ratso\\\" Sloman\"", List.of(0)),
        Arguments.of("author=\"Larry \\\"Ratso\\\"\"", List.of()),
        Arguments.of("author=\"Larry Ratso Sloman\"", List.of()),
        Arguments.of("title=\"road with bob\"", List.of(0)),
        Arguments.of("title=\"Road the bob\"", List.of(0)),  // "the" and "with" are both stop words.
        Arguments.of("title=\"bob the road\"", List.of()),
        Arguments.of("title all \"bob the road\"", List.of(0)),
        Arguments.of("title=\"with cry\"", List.of(1)), // "with" a stop word
        Arguments.of("title=\"bob cry\"", List.of()),
        Arguments.of("author=\"\"", List.of(0, 1)),
        Arguments.of("author = Garnet Mi?ms", List.of(1))
    );
  }

  @ParameterizedTest
  @MethodSource("cqlQueries")
  void testCqlQueries(String q, List<Integer> exp, Vertx vertx, VertxTestContext context) {
    test(q, exp).onComplete(context.succeedingThenComplete());
  }

}
