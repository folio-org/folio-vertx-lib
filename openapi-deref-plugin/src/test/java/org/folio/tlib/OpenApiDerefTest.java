package org.folio.tlib;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class OpenApiDerefTest {

  @Test
  void testDeref() throws Exception {
    String input = "src/test/resources/openapi/reftest.yaml";
    String output = "target/generated-resources/openapi/reftest.deref.yaml";
    OpenApiDeref.fix(input, output);
    // read output file into memory
    String outputContent = new String(Files.readAllBytes(Paths.get(output)), StandardCharsets.UTF_8);
    assertNotNull(outputContent);
    // check that $ref is not found anywhere
    assertFalse(outputContent.contains("$ref"));
  }
}
