package org.folio.tlib.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;
import java.util.Collections;
import java.util.UUID;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;
import org.folio.tlib.postgres.PgCqlField;
import org.folio.tlib.postgres.PgCqlQuery;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.tlib.util.TenantUtil;

public class MyApi implements RouterCreator, TenantInitHooks {
  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return RouterBuilder.create(vertx, "openapi/myapi-1.0.yaml")
        .map(routerBuilder -> {
          handlers(vertx, routerBuilder);
          return routerBuilder.createRouter();
        });
  }

  private void handlers(Vertx vertx, RouterBuilder routerBuilder) {
    routerBuilder
        .operation("postBook") // operationId in spec
        .handler(ctx -> postBook(vertx, ctx)
            .onFailure(cause ->
                ctx.response().setStatusCode(500).end(cause.getMessage())
            ));
    routerBuilder
        .operation("getBooks")
        .handler(ctx -> getBooks(vertx, ctx)
            .onFailure(cause ->
                ctx.response().setStatusCode(500).end(cause.getMessage())
            ));
  }

  private String getMyTable(TenantPgPool pool) {
    return pool.getSchema() + ".mytable";
  }

  @Override
  public Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    if (!tenantAttributes.containsKey("module_to")) {
      return Future.succeededFuture(); // doing nothing for disable
    }
    TenantPgPool pool = TenantPgPool.pool(vertx, tenant);
    Future<Void> future = pool.query(
            "CREATE TABLE IF NOT EXISTS " + getMyTable(pool)
                + "(id UUID PRIMARY KEY, title TEXT, index_title TEXT)")
        .execute().mapEmpty();
    JsonArray parameters = tenantAttributes.getJsonArray("parameters");
    if (parameters != null) {
      for (int i = 0; i < parameters.size(); i++) {
        JsonObject parameter = parameters.getJsonObject(i);
        if ("loadSample".equals(parameter.getString("key"))
            && "true".equals(parameter.getString("value"))) {
          future = future.compose(x ->
              pool.preparedQuery("INSERT INTO " + getMyTable(pool)
                      + "(id, title, index_title) VALUES ($1, $2, $3)")
                  .execute(Tuple.of(UUID.randomUUID(), "First title", "first title")).mapEmpty()
          );
          future = future.compose(x ->
              pool.preparedQuery("INSERT INTO " + getMyTable(pool)
                      + "(id, title, index_title) VALUES ($1, $2, $3)")
                  .execute(Tuple.of(UUID.randomUUID(), "Second title", "second title")).mapEmpty()
          );
        }
      }
    }
    return future;
  }

  private String createQueryMyTable(RoutingContext ctx, TenantPgPool pool) {
    RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    PgCqlQuery pgCqlQuery = PgCqlQuery.query();
    RequestParameter query = params.queryParameter("query");
    pgCqlQuery.parse(query == null ? null : query.getString());
    pgCqlQuery.addField(new PgCqlField("cql.allRecords", PgCqlField.Type.ALWAYS_MATCHES));
    pgCqlQuery.addField(new PgCqlField("id", PgCqlField.Type.UUID));
    pgCqlQuery.addField(new PgCqlField("title", PgCqlField.Type.FULLTEXT));
    String sql = "SELECT * FROM " + getMyTable(pool);
    String where = pgCqlQuery.getWhereClause();
    if (where != null) {
      sql = sql + " WHERE " + where;
    }
    String orderBy = pgCqlQuery.getOrderByClause();
    if (orderBy != null) {
      sql = sql + " ORDER BY " + orderBy;
    }
    return sql;
  }

  private Future<Void> getBooks(Vertx vertx, RoutingContext ctx) {
    String tenant = TenantUtil.tenant(ctx);
    TenantPgPool pool = TenantPgPool.pool(vertx, tenant);
    String sql = createQueryMyTable(ctx, pool);
    return SqlTemplate.forQuery(pool.getPool(), sql)
        .mapTo(BookRowMapper.INSTANCE)
        .execute(Collections.emptyMap())
        .map(books -> {
          JsonArray ar = new JsonArray();
          for (Book book : books) {
            ar.add(JsonObject.mapFrom(book));
          }
          ctx.response().putHeader("Content-Type", "application/json");
          ctx.response().setStatusCode(200);
          JsonObject result = new JsonObject().put("books", books);
          ctx.response().end(result.encode());
          return null;
        });
  }

  private Future<Void> postBook(Vertx vertx, RoutingContext ctx) {
    String tenant = TenantUtil.tenant(ctx);
    TenantPgPool pool = TenantPgPool.pool(vertx, tenant);
    Book book = ctx.body().asPojo(Book.class);

    return SqlTemplate.forUpdate(pool.getPool(), "INSERT INTO " + getMyTable(pool)
            + " VALUES (#{id},#{title},#{indexTitle})")
        .mapFrom(Book.class)
        .execute(book)
        .map(res -> {
          ctx.response().setStatusCode(204).end();
          return null;
        });
  }
}
