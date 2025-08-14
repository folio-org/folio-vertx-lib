package org.folio.tlib;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.openapi.contract.OpenAPIContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({VertxExtension.class})
public class OpenApiRefTest {
  @Test
  void testJsonOutput(Vertx vertx, VertxTestContext context) throws Exception {
    String input = "openapi/reftest.yaml";
    String output = "target/reftest-resolved.json";
    OpenApiRef.fix(input, output);
    OpenAPIContract.from(vertx, output)
         .onComplete(context.succeedingThenComplete());
  }

  @Test
  void testYamlOutput(Vertx vertx, VertxTestContext context) throws Exception {
    OpenAPIContract.from(vertx, OpenApiRef.fix("openapi/reftest.yaml"))
         .onComplete(context.succeedingThenComplete());
  }
}
