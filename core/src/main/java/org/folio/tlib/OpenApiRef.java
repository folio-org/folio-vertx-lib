package org.folio.tlib;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.File;
import java.io.IOException;

/**
 * Class for resolving OpenAPI $ref references.
 */
public class OpenApiRef {
  /**
   * Resolves $ref in OpenAPI, suitable for Vert.x OpenAPI parser.
   *
   * @param path local-URI to OpenAPI YAML file
   * @return resolved file in target
   * @throws IOException if file could be found or similar
   */
  public static String fix(String path) throws IOException {
    // get filename portion of path
    String refFilename = new File(path).getName();
    refFilename = "target/" + refFilename.replace(".yaml", "_ref.yaml");
    fix(path, refFilename);
    return refFilename;
  }

  static void fix(String inputPath, String outputPath) throws IOException {
    ParseOptions parseOptions = new ParseOptions();
    parseOptions.setResolve(true);
    SwaggerParseResult result = new OpenAPIV3Parser().readLocation(inputPath, null, parseOptions);
    OpenAPI openApi = result.getOpenAPI();
    if (openApi == null) {
      throw new IOException("Failed to parse OpenAPI: " + result.getMessages());
    }
    ObjectMapper mapper;
    if (outputPath.endsWith(".yaml")) {
      mapper = new ObjectMapper(new YAMLFactory());
    } else {
      mapper = new ObjectMapper(); // JSON output
    }
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // Convert OpenAPI object to a tree for post-processing
    JsonNode tree = mapper.valueToTree(openApi);
    // swagger parser produces some properties that Vert.x OpenApi does not recognize. Omit these.
    removeKeysRecursive(tree,
        "exampleSetFlag", "extensions", "jsonSchema", "servers", "style", "types", "valueSetFlag");

    mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), tree);
  }

  private static void removeKeysRecursive(JsonNode node, String... keys) {
    if (node.isObject()) {
      java.util.Iterator<String> fieldNames = node.fieldNames();
      java.util.List<String> toRemove = new java.util.ArrayList<>();
      while (fieldNames.hasNext()) {
        String field = fieldNames.next();
        for (String k : keys) {
          if (field.equals(k)) {
            toRemove.add(field);
          }
        }
      }
      for (String field : toRemove) {
        ((ObjectNode) node).remove(field);
      }
      // Recurse into children
      node.fields().forEachRemaining(e -> removeKeysRecursive(e.getValue(), keys));
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        removeKeysRecursive(item, keys);
      }
    }
  }
}
