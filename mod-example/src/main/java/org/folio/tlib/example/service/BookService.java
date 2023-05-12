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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.HttpResponse;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;
import org.folio.tlib.example.data.Book;
import org.folio.tlib.example.storage.BookStorage;
import org.folio.tlib.util.TenantUtil;

/**
 * Book service.
 */
public class BookService implements RouterCreator, TenantInitHooks {

  public static final int BODY_LIMIT = 65536; // 64 kb

  private final Logger log = LogManager.getLogger(BookService.class);

  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return RouterBuilder.create(vertx, "openapi/books-1.0.yaml")
        .map(routerBuilder -> {
          // https://vertx.io/docs/vertx-web/java/#_limiting_body_size
          routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(BODY_LIMIT));
          handlers(vertx, routerBuilder);
          return routerBuilder.createRouter();
        })
        .onSuccess(res -> log.info("OpenAPI parsed OK"));
  }

  private void handleContextFailure(RoutingContext ctx) {
    ctx.response().setStatusCode(ctx.statusCode());
    String msg = ctx.failure() != null ? ctx.failure().getMessage()
        : HttpResponseStatus.valueOf(ctx.statusCode()).reasonPhrase();
    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
    ctx.response().end(msg);
  }

  /**
   * Set up our handlers.
   * <p>
   *   Each handler returns Future. If that is successful, it it assumed that
   *   the handler has returned a HTTP response (including HTTP errors).
   *   If the handler returns Future failure, we leave it to responseError to make
   *   a response.
   *   If the handler throws an exception OR if the the OpenAPI validations fails, then
   *   onFailure kicks in and handleContextFailure kicks in.
   * </p>
   *
   * @param vertx Vert.x
   * @param routerBuilder OpenAPI router builder
   */
  private void handlers(Vertx vertx, RouterBuilder routerBuilder) {
    routerBuilder
        .operation("postBook") // operationId in spec
        .handler(ctx -> postBook(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::handleContextFailure);
    routerBuilder
        .operation("getBook")
        .handler(ctx -> getBook(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::handleContextFailure);
    routerBuilder
        .operation("getBooks")
        .handler(ctx -> getBooks(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::handleContextFailure);
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
