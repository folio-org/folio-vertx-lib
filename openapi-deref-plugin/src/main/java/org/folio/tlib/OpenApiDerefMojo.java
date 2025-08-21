package org.folio.tlib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo to dereference OpenAPI $ref and produce a single file.
 */
@Mojo(name = "dereference", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class OpenApiDerefMojo extends AbstractMojo {

  /**
   * Path to the input OpenAPI YAML file.
   */
  @Parameter(property = "input", required = false,
      defaultValue = "${basedir}/src/main/resources/openapi/*.yaml")
  private String input;

  /**
   * Path to the output file (YAML or JSON).
   */
  @Parameter(property = "output", required = false,
      defaultValue = "${project.build.directory}/classes/openapi")
  private String output;

  @Override
  public void execute() throws MojoExecutionException {
    getLog().info("Dereferencing OpenAPI: " + input + " -> " + output);
    List<String> files = new ArrayList<>();
    try {
      files = OpenApiDeref.mapFilesWithPattern(input, output);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to map OpenAPI files", e);
    }
    for (int i = 0; i < files.size(); i += 2) {
      String inputFile = files.get(i);
      String outputFile = files.get(i + 1);
      try {
        OpenApiDeref.fix(inputFile, outputFile);
      } catch (IOException e) {
        throw new MojoExecutionException("Failed to dereference OpenAPI file " + inputFile, e);
      }
      getLog().info("Dereferenced OpenAPI written to: " + outputFile);
    }
  }
}
