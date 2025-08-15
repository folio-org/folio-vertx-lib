package org.folio.tlib;

import java.io.IOException;
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
  @Parameter(property = "input.yaml", required = true)
  private String input;

  /**
   * Path to the output file (YAML or JSON).
   */
  @Parameter(property = "output.yaml", required = true)
  private String output;

  @Override
  public void execute() throws MojoExecutionException {
    getLog().info("Dereferencing OpenAPI: " + input + " -> " + output);
    try {
      // I want to generate all leading directories in output path
      new OpenApiDeref(input, output);
      getLog().info("Dereferenced OpenAPI written to: " + output);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to dereference OpenAPI spec", e);
    }
  }
}
