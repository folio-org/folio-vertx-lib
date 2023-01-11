package org.folio.tlib.postgres;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.PrepareOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.tlib.postgres.impl.TenantPgPoolImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@ExtendWith({VertxExtension.class})
class TenantPgPoolTest {
  private final static Logger log = LogManager.getLogger(TenantPgPoolTest.class);

  @Container
  public static PostgreSQLContainer<?> postgresSQLContainer = TenantPgPoolContainer.create();

  static final String KEY_PATH = "/var/lib/postgresql/data/server.key";
  static final String CRT_PATH = "/var/lib/postgresql/data/server.crt";
  static final String CONF_PATH = "/var/lib/postgresql/data/postgresql.conf";
  static final String CONF_BAK_PATH = "/var/lib/postgresql/data/postgresql.conf.bak";

  // execute commands in container (stolen from Okapi's PostgresHandleTest
  static void exec(String... command) {
    try {
      org.testcontainers.containers.Container.ExecResult execResult = postgresSQLContainer.execInContainer(command);
      if (execResult.getExitCode() != 0) {
        log.info("{} {}", execResult.getExitCode(), String.join(" ", command));
        log.info("stderr: {}", execResult.getStderr());
        log.info("stdout: {}", execResult.getStdout());
      }
    } catch (InterruptedException|IOException|UnsupportedOperationException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Append each entry to postgresql.conf and reload it into postgres.
   * Appending a key=value entry has precedence over any previous entries of the same key.
   * Stolen from Okapi's PostgresHandleTest
   */
  static void configure(String... configEntries) {
    exec("cp", "-p", CONF_BAK_PATH, CONF_PATH);  // start with unaltered config
    for (String configEntry : configEntries) {
      exec("sh", "-c", "echo '" + configEntry + "' >> " + CONF_PATH);
    }
    exec("su-exec", "postgres", "pg_ctl", "reload");
  }

  @BeforeAll
  static void beforeAll() {
    MountableFile serverKeyFile = MountableFile.forClasspathResource("server.key");
    MountableFile serverCrtFile = MountableFile.forClasspathResource("server.crt");
    postgresSQLContainer.copyFileToContainer(serverKeyFile, KEY_PATH);
    postgresSQLContainer.copyFileToContainer(serverCrtFile, CRT_PATH);
    exec("chown", "postgres.postgres", KEY_PATH, CRT_PATH);
    exec("chmod", "400", KEY_PATH, CRT_PATH);
    exec("cp", "-p", CONF_PATH, CONF_BAK_PATH);
  }

  @BeforeEach
  void before() {
    configure();  // default postgresql.conf
    TenantPgPool.setServerPem(null);
    TenantPgPool.setModule("mod-foo");
  }

  @AfterEach
  void after(Vertx vertx, VertxTestContext context) {
    TenantPgPool.closeAll().onComplete(context.succeedingThenComplete());
  }

  /**
   * Create a TenantPgPool, run the mapper on it, close the pool,
   * and return the result from the mapper.
   */
  private <T> Future<T> withPool(Vertx vertx, Function<TenantPgPool, Future<T>> mapper) {
    TenantPgPool pool = TenantPgPool.pool(vertx, "diku");
    Future<T> future = mapper.apply(pool);
    return future.eventually(x -> pool.close());
  }

  @Test
  void testBadModule() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> TenantPgPoolImpl.setModule("mod'a"));
  }

  @Test
  void testNoSetModule(Vertx vertx, VertxTestContext context) {
    TenantPgPoolImpl.setModule(null);
    Assertions.assertThrows(IllegalStateException.class, () -> TenantPgPoolImpl.tenantPgPool(vertx, "diku"));
    context.completeNow();
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void queryOk(Vertx vertx, VertxTestContext context) {
    withPool(vertx, pool -> pool
        .query("SELECT count(*) FROM pg_database")
        .execute())
        .onComplete(context.succeedingThenComplete());
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void useSqlTemplate(Vertx vertx, VertxTestContext context) {
    withPool(vertx, pool ->
        SqlTemplate.forQuery(pool.getPool(), "SELECT count(*) FROM pg_database")
            .execute(Collections.emptyMap()))
        .onComplete(context.succeedingThenComplete());
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void preparedQueryOptionsOk(Vertx vertx, VertxTestContext context) {
    withPool(vertx, pool -> pool
        .preparedQuery("SELECT * FROM pg_database WHERE datname=$1", new PrepareOptions())
        .execute(Tuple.of("postgres")))
        .onComplete(context.succeedingThenComplete());
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void executeOk(Vertx vertx, VertxTestContext context) {
    withPool(vertx, pool -> pool
        .execute("SELECT * FROM pg_database WHERE datname=$1", Tuple.of("postgres")))
        .onComplete(context.succeedingThenComplete());
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void executeAnalyze(Vertx vertx, VertxTestContext context) {
    vertx.getOrCreateContext().config().put("explain_analyze", Boolean.TRUE);
    withPool(vertx, pool -> pool
        .execute("SELECT * FROM pg_database WHERE datname=$1", Tuple.of("postgres")))
        .onComplete(context.succeeding(x -> {
          vertx.getOrCreateContext().config().remove("explain_analyze");
          context.completeNow();
        }));
  }

  @Test
  void applicationName(Vertx vertx, VertxTestContext context) {
    withPool(vertx, pool -> pool
        .query("SELECT application_name FROM pg_stat_activity WHERE pid = pg_backend_pid()")
        .execute())
    .onComplete(context.succeeding(rowSet -> {
      assertThat(rowSet.iterator().next().getString(0), is("mod_foo"));
      context.completeNow();
    }));
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void getConnection1(Vertx vertx, VertxTestContext context) {
    withPool(vertx, pool -> pool.withConnection(con ->
        con.query("SELECT count(*) FROM pg_database").execute()))
    .onComplete(context.succeedingThenComplete());
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void getConnection2(Vertx vertx, VertxTestContext context) {
    withPool(vertx, pool ->
        Future.<SqlConnection>future(promise -> pool.getConnection(promise))
        .compose(con -> con.query("SELECT count(*) FROM pg_database").execute()))
    .onComplete(context.succeedingThenComplete());
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void execute1(Vertx vertx, VertxTestContext context) {
    List<String> list = new LinkedList<>();
    list.add("CREATE TABLE a (year int)");
    list.add("SELECT * FROM a");
    list.add("DROP TABLE a");
    withPool(vertx, pool -> pool.execute(list))
        .onComplete(context.succeedingThenComplete());
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void execute2(Vertx vertx, VertxTestContext context) {
    // execute not using a transaction as this test shows.
    List<String> list = new LinkedList<>();
    list.add("CREATE TABLE a (year int)");
    list.add("ALTER TABLE a RENAME TO b");  // renames
    list.add("DROP TABLOIDS b");            // fails
    list.add("ALTER TABLE b RENAME TO c");  // not executed
    withPool(vertx, pool ->
        pool.execute(list)
        .onComplete(x -> assertThat(x.failed(), is(true)))
        .recover(x -> pool.execute(List.of("DROP TABLE b")))) // better now
    .onComplete(context.succeedingThenComplete());
  }

  @Test
  void testGetPoolOptions(Vertx vertx, VertxTestContext context) {
    TenantPgPool.setMaxPoolSize("4");
    TenantPgPool pool = TenantPgPool.pool(vertx, "diku");
    int sz = pool.getPoolOptions().getMaxSize();
    assertThat(sz, is(4));
    context.completeNow();
  }

  @Test
  void testSSL(Vertx vertx, VertxTestContext context) throws IOException {
    Assumptions.assumeTrue(System.getenv("DB_HOST") == null);
    Assumptions.assumeTrue(System.getenv("DB_PORT") == null);
    configure("ssl=on");
    TenantPgPool.setMaxPoolSize("3");
    TenantPgPool.setServerPem(new String(TenantPgPoolTest.class.getClassLoader()
        .getResourceAsStream("server.crt").readAllBytes()));

    withPool(vertx, pool -> pool
        .query("SELECT version FROM pg_stat_ssl WHERE pid = pg_backend_pid()")
        .execute())
        .onComplete(context.succeeding(rowSet -> {
          assertThat(rowSet.iterator().next().getString(0), is("TLSv1.3"));
          context.completeNow();
        }));
  }

  @Test
  void size(Vertx vertx, VertxTestContext context) {
    final TenantPgPool pool = TenantPgPool.pool(vertx, "diku");
    assertThat(pool.size(), is(0));
    pool.query("SELECT count(*) FROM pg_database")
        .execute()
        .onComplete(context.succeeding(x -> {
          assertThat(pool.size(), is(1));
          pool.close()
              .onComplete(context.succeeding(y -> {
                assertThat(pool.size(), is(0));
                context.completeNow();
              }));
        }));
  }

  @Test
  @SuppressWarnings("squid:S2699") // "Add at least one assertion" SQ does not know about context.*
  void close(Vertx vertx, VertxTestContext context) {
    TenantPgPool pool = TenantPgPool.pool(vertx, "diku");
    pool.close(context.succeedingThenComplete());
  }

  @Test
  void closeAll(Vertx vertx, VertxTestContext context) {
    TenantPgPool pool = TenantPgPool.pool(vertx, "diku");
    assertThat(pool.size(), is(0));
    TenantPgPool.closeAll().onComplete(context.succeedingThenComplete());
  }

  @Test
  void connectHandler(Vertx vertx, VertxTestContext context) {
    TenantPgPool pool = TenantPgPool.pool(vertx, "diku");
    pool.connectHandler(conn ->
        conn.query("CREATE TEMP TABLE connecthandler()")
            .execute()
            .eventually(x -> conn.close())
    );
    pool.withConnection(conn -> conn.preparedQuery("SELECT * FROM connecthandler").execute())
        .onComplete(context.succeeding(rowSet -> {
          assertThat(rowSet.size(), is(0));
          context.completeNow();
        }));
  }
}
