package org.folio.tlib;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiDerefTest {

  @Test
  void testMapFilesWithPattern1() throws Exception{
    List<String> files = OpenApiDeref.mapFilesWithPattern("src/test/resources/openapi/*.yaml", "target/generated-resources/openapi");
    assertEquals(2, files.size());
    assertTrue(files.get(0).endsWith("src/test/resources/openapi/reftest.yaml"), "Input file not right: " + files.get(0));
    assertTrue(files.get(1).endsWith("target/generated-resources/openapi/reftest.yaml"), "Output file not right: " + files.get(1));
  }

  @Test
  void testMapFilesWithPattern2() throws Exception {
    List<String> files = OpenApiDeref.mapFilesWithPattern("src/test/resources/openapi/*.nope", "target/generated-resources/openapi");
    assertEquals(0, files.size());
  }

  @Test
  void testMapFilesWithPattern3() throws Exception {
    assertThrows(IOException.class, () -> {
      OpenApiDeref.mapFilesWithPattern("src1/test1/1.nope", "target/generated-resources/openapi");
    });
  }

  @Test
  void testMapFilesWithPattern4() {
    assertThrows(IOException.class, () -> {
      OpenApiDeref.mapFilesWithPattern("onlyfilename.yaml", "target/generated-resources/openapi");
    });
  }

  @Test
  void testDerefYaml() throws Exception {
    String input = "src/test/resources/openapi/reftest.yaml";
    String output = "target/generated-resources/openapi/reftest.deref.yaml";
    OpenApiDeref.fix(input, output);
    // read output file into memory
    String outputContent = new String(Files.readAllBytes(Paths.get(output)), StandardCharsets.UTF_8);
    assertNotNull(outputContent);
    // check that $ref is not found anywhere
    assertFalse(outputContent.contains("$ref: headers"));
  }

  @Test
  void testDerefJson() throws Exception {
    String input = "src/test/resources/openapi/reftest.yaml";
    String output = "target/generated-resources/openapi/reftest.deref.json";
    OpenApiDeref.fix(input, output);
    // read output file into memory
    String outputContent = new String(Files.readAllBytes(Paths.get(output)), StandardCharsets.UTF_8);
    assertNotNull(outputContent);
    // check that $ref is not found anywhere
    assertFalse(outputContent.contains("$ref: headers"));
  }

  @Test
  void testFailToParse() {
    String input = "src/test/resources/openapi/headers/okapi-token.yaml";
    String output = "target/generated-resources/openapi/okapi-token.deref.json";
    assertThrows(java.io.IOException.class, () -> {
      OpenApiDeref.fix(input, output);
    });
  }

  @Test
  void testNoFile() {
    String input = "src/test/resources/openapi/headers/no-file-en.yaml";
    String output = "target/generated-resources/openapi/no-file.deref.json";
    assertThrows(java.io.IOException.class, () -> {
      OpenApiDeref.fix(input, output);
    });
  }

}
