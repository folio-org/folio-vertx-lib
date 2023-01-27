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

import io.vertx.sqlclient.Tuple;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldAlwaysMatches;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldText;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldUuid;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;

import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

@Testcontainers
@ExtendWith({VertxExtension.class})
class PgCqlStorageTest {

  @Container
  static final PostgreSQLContainer<?> container = TenantPgPoolContainer.create();

  public static PgPool pgPool;

  static List<Tuple> batch = List.of(
      Tuple.of(UUID.randomUUID(), "On the road with Bob Dylan", "Larry \"Ratso\" Sloman"),
      Tuple.of(UUID.randomUUID(), "Cry baby", "Garnet Mimms"),
      Tuple.of(UUID.randomUUID(), "only title", null),
      Tuple.of(UUID.randomUUID(), null, null)
  );

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
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

  static Future<Void> insertSample() {
    return pgPool.preparedQuery("INSERT INTO entries (id, title, author) VALUES ($1, $2, $3)")
        .executeBatch(batch)
        .mapEmpty();
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext context) {
    pgPool.close().onComplete(context.succeedingThenComplete());
  }

  private Future<Void> test(String query, List<Integer> expected) {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("cql.allRecords", new PgCqlFieldAlwaysMatches());
    pgCqlDefinition.addField("id", new PgCqlFieldUuid());
    pgCqlDefinition.addField("stitle", new PgCqlFieldText().withColumn("title"));
    pgCqlDefinition.addField("title", new PgCqlFieldText().withFullText("english"));
    pgCqlDefinition.addField("author", new PgCqlFieldText().withLikeOps());
    PgCqlQuery parse = pgCqlDefinition.parse(query);
    String where = parse.getWhereClause() == null ? ""
        : "WHERE " + parse.getWhereClause();
    return pgPool.query("SELECT * FROM entries " + where)
        .execute()
        .compose(rowSet -> {
          Set<Integer> got = new HashSet<>();
          rowSet.forEach(row -> {
            UUID id = row.getUUID("id");
            Iterator<Tuple> iterator = batch.iterator();
            int off = 0;
            while (iterator.hasNext() && !iterator.next().getUUID(0).equals(id)) {
              off++;
            }
            got.add(off);
          });
          assertThat(got, containsInAnyOrder(expected.toArray()));
          return Future.succeededFuture();
        });
  }

  static Stream<Arguments> cqlQueries() {
    return Stream.of(
        Arguments.of("cql.allRecords=1", List.of(0, 1, 2, 3)),
        Arguments.of("id=" + batch.get(0).getUUID(0), List.of(0)),
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

  private Future<Void> matchField(PgCqlFieldType pgCqlFieldType, String cql, String value, boolean expected) {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("field", pgCqlFieldType);
    PgCqlQuery parse = pgCqlDefinition.parse("field " + cql);
    return matchValue(parse, "field", value, expected);
  }

  private Future<Void> matchValue(PgCqlQuery parse, String column, String value, boolean expected) {
    String sql = "SELECT " + parse.getWhereClause() + " FROM (SELECT $1 " + column + ") x";
    return pgPool.preparedQuery(sql)
        .execute(Tuple.of(value))
        .map(rowSet -> {
          assertThat(sql, rowSet.iterator().next().getBoolean(0), is(expected));
          return null;
        });
  }

  static Stream<Arguments> fieldValueQueries() {
    return Stream.of(
        Arguments.of(new PgCqlFieldText().withExact(), "= \"foo\\\\bar\"", "foo\\bar", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"foo\\\\bar\"", "foo\\bar", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"foo\\\\ba?\"", "foo\\bar", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"foo_%ba?\"", "foo_%bar", true),
        Arguments.of(new PgCqlFieldText().withFullText(), "= \"foo\\\\bar\"", "foo\\bar", true),
        Arguments.of(new PgCqlFieldText().withFullText(), "= \"foo bar\"", "foo\\bar", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"*\"", "x", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\\\\"", "\\", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\\\\"", "x", false),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\^\"", "^", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\^\"", "x", false),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\?\"", "?", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\?\"", "x", false),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\_\"", "_", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\_\"", "x", false),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\*\"", "*", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\*\"", "x", false),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\%\"", "%", true),
        Arguments.of(new PgCqlFieldText().withLikeOps(), "= \"\\%\"", "x", false)
    );
  }

  @ParameterizedTest
  @MethodSource("fieldValueQueries")
  void testCqlQueries(PgCqlFieldType pgCqlFieldType, String query, String value, boolean expected,
      Vertx vertx, VertxTestContext context) {
    matchField(pgCqlFieldType, query, value, expected).onComplete(context.succeedingThenComplete());
  }


}
