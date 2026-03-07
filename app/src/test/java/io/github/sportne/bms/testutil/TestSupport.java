package io.github.sportne.bms.testutil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared helper methods used by unit tests. */
public final class TestSupport {
  private TestSupport() {}

  /** Resolves a classpath resource to an absolute path for test usage. */
  public static Path resourcePath(String resourcePath) {
    try {
      var resourceUrl =
          TestSupport.class
              .getClassLoader()
              .getResource(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
      if (resourceUrl == null) {
        throw new IllegalArgumentException("Resource not found: " + resourcePath);
      }
      return Path.of(resourceUrl.toURI());
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("Invalid resource URI: " + resourcePath, exception);
    }
  }

  /** Reads a classpath resource as UTF-8 text. */
  public static String readResource(String resourcePath) {
    try {
      return Files.readString(resourcePath(resourcePath), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read resource: " + resourcePath, exception);
    }
  }

  /** Finds `spec/xsd/BinaryMessageSchema.xsd` by walking up from the test working directory. */
  public static Path repositoryXsdPath() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve("spec").resolve("xsd").resolve("BinaryMessageSchema.xsd");
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException(
        "Unable to locate spec/xsd/BinaryMessageSchema.xsd from test cwd.");
  }
}
