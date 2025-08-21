package org.folio.tlib.example.service;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;
import io.vertx.openapi.validation.ValidatedRequest;
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

  private final Logger log = LogManager.getLogger(BookService.class);

  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return OpenAPIContract.from(vertx, "openapi/books-1.0.yaml")
      .map(contract -> {
        RouterBuilder routerBuilder = RouterBuilder.create(vertx, contract);
        handlers(vertx, routerBuilder);
        return routerBuilder.createRouter();
      })
      .onSuccess(res -> log.info("OpenAPI parsed OK"));
  }

  private void handleContextFailure(RoutingContext ctx) {
    ctx.response().setStatusCode(ctx.statusCode());
    String msg;
    if (ctx.failure() == null) {
      msg = HttpResponseStatus.valueOf(ctx.statusCode()).reasonPhrase();
    } else if (ctx.failure().getCause() == null) {
      msg = ctx.failure().getMessage();
    } else {
      msg = ctx.failure().getCause().getMessage();
    }
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
    routerBuilder.getRoute("postBook") // operationId in spec
        .addHandler(ctx -> postBook(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .addFailureHandler(this::handleContextFailure);
    routerBuilder.getRoute("getBook")
        .addHandler(ctx -> getBook(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .addFailureHandler(this::handleContextFailure);
    routerBuilder.getRoute("getBooks")
        .addHandler(ctx -> getBooks(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .addFailureHandler(this::handleContextFailure);
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
    UUID id = UUID.fromString(ctx.pathParam("id"));
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
    ValidatedRequest validatedRequest =
        ctx.get(RouterBuilder.KEY_META_DATA_VALIDATED_REQUEST);
    Book book = JsonObject.mapFrom(validatedRequest.getBody().getJsonObject()).mapTo(Book.class);
    BookStorage storage = new BookStorage(vertx, tenant);
    return storage.postBook(book)
        .map(res -> {
          ctx.response().setStatusCode(204).end();
          return null;
        });
  }
}
