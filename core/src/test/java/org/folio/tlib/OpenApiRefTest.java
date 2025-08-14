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
  void test1(Vertx vertx, VertxTestContext context) throws Exception {
    String input = "openapi/reftest.yaml";
    String output = "target/reftest-resolved.yaml";
    OpenApiRef.fix(input, output);
    OpenAPIContract.from(vertx, "target/reftest-resolved.yaml")
         .onComplete(context.succeedingThenComplete());
  }

  @Test
  void test2(Vertx vertx, VertxTestContext context) throws Exception {
    OpenAPIContract.from(vertx, OpenApiRef.fix("openapi/reftest.yaml"))
         .onComplete(context.succeedingThenComplete());
  }

  @Test
  void writeResolvedOpenApi() throws Exception {
    // Reads the test YAML, resolves refs, writes to output file (YAML)
    String input = "openapi/reftest.yaml";
    String output = "target/reftest-resolved.yaml";
    OpenApiRef.fix(input, output);
    // Optionally, assert file exists or print path
    System.out.println("Resolved OpenAPI written to: " + output);
  }
}
