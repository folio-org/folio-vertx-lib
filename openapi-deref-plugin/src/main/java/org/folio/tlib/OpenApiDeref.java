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
public class OpenApiDeref {
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

    new File(outputPath).getParentFile().mkdirs();
    mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputPath), tree);
  }

  private static void removeKeysRecursive(JsonNode node, String... keys) {
    if (node.isObject()) {
      ObjectNode obj = (ObjectNode) node;
      for (String key : keys) {
        obj.remove(key);
      }
      obj.fields().forEachRemaining(entry -> removeKeysRecursive(entry.getValue(), keys));
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        removeKeysRecursive(item, keys);
      }
    }
  }
}
