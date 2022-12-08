package org.folio.tlib.example.service;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import java.util.UUID;
import org.folio.okapi.common.HttpResponse;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;
import org.folio.tlib.example.data.Book;
import org.folio.tlib.example.storage.BookStorage;
import org.folio.tlib.util.TenantUtil;

public class BookService implements RouterCreator, TenantInitHooks {

  public static final int BODY_LIMIT = 65536; // 64 kb

  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return RouterBuilder.create(vertx, "openapi/books-1.0.yaml")
        .map(routerBuilder -> {
          // https://vertx.io/docs/vertx-web/java/#_limiting_body_size
          routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(BODY_LIMIT));
          handlers(vertx, routerBuilder);
          return routerBuilder.createRouter();
        });
  }

  private void failureHandler(RoutingContext ctx) {
    ctx.response().setStatusCode(ctx.statusCode());
    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
    ctx.response().end(HttpResponseStatus.valueOf(ctx.statusCode()).reasonPhrase());
  }

  private void handlers(Vertx vertx, RouterBuilder routerBuilder) {
    routerBuilder
        .operation("postBook") // operationId in spec
        .handler(ctx -> postBook(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::failureHandler);
    routerBuilder
        .operation("getBook")
        .handler(ctx -> getBook(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::failureHandler);

    routerBuilder
        .operation("getBooks")
        .handler(ctx -> getBooks(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::failureHandler);
  }

  @Override
  public Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    BookStorage storage = new BookStorage(vertx, tenant);
    return storage.init(tenantAttributes);
  }

  private Future<Void> getBooks(Vertx vertx, RoutingContext ctx) {
    String tenant = TenantUtil.tenant(ctx);
    BookStorage storage = new BookStorage(vertx, tenant);
    return storage.getBooks(ctx)
        .map(books -> {
          JsonArray ar = new JsonArray();
          for (Book book : books) {
            ar.add(JsonObject.mapFrom(book));
          }
          JsonObject result = new JsonObject().put("books", books);
          HttpResponse.responseJson(ctx, 200).end(result.encode());
          return null;
        });
  }

  private Future<Void> getBook(Vertx vertx, RoutingContext ctx) {
    String tenant = TenantUtil.tenant(ctx);

    RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    UUID id = UUID.fromString(params.pathParameter("id").getString());
    BookStorage storage = new BookStorage(vertx, tenant);
    return storage.getBook(id)
        .map(book -> {
          if (book == null) {
            HttpResponse.responseError(ctx, 404, "Not found " + id);
          } else {
            HttpResponse.responseJson(ctx, 200).end(JsonObject.mapFrom(book).encode());
          }
          return null;
        });
  }

  private Future<Void> postBook(Vertx vertx, RoutingContext ctx) {
    String tenant = TenantUtil.tenant(ctx);
    BookStorage storage = new BookStorage(vertx, tenant);
    Book book = ctx.body().asPojo(Book.class);
    return storage.postBook(book)
        .map(res -> {
          ctx.response().setStatusCode(204).end();
          return null;
        });
  }
}
