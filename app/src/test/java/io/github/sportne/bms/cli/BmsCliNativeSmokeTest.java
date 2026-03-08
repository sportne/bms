package io.github.sportne.bms.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.testutil.TestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Basic smoke tests that execute the natively compiled CLI binary. */
class BmsCliNativeSmokeTest {
  private static final String NATIVE_EXECUTABLE_PROPERTY = "bms.native.executable";

  @TempDir Path tempDir;

  /** Contract: native `validate` succeeds for a known-good foundation spec. */
  @Test
  void nativeValidateCommandReturnsSuccessForValidSpec() {
    CommandResult result = runNativeCommand("validate", fixturePath("specs/valid-foundation.xml"));

    assertEquals(0, result.exitCode());
    assertTrue(result.output().contains("Validation succeeded"));
  }

  /** Contract: native `validate` fails for an invalid spec and reports XSD diagnostics. */
  @Test
  void nativeValidateCommandReturnsSpecErrorForInvalidSpec() {
    CommandResult result =
        runNativeCommand("validate", fixturePath("specs/missing-schema-namespace.xml"));

    assertEquals(1, result.exitCode());
    assertTrue(result.output().contains("XSD"));
  }

  /** Contract: native `generate` succeeds and writes both Java and C++ outputs. */
  @Test
  void nativeGenerateCommandProducesJavaAndCppOutput() {
    Path javaOutputDir = tempDir.resolve("java");
    Path cppOutputDir = tempDir.resolve("cpp");

    CommandResult result =
        runNativeCommand(
            "generate",
            fixturePath("specs/valid-foundation.xml"),
            "--java",
            javaOutputDir.toString(),
            "--cpp",
            cppOutputDir.toString());

    assertEquals(0, result.exitCode());
    assertTrue(Files.exists(javaOutputDir.resolve("acme/telemetry/Header.java")));
    assertTrue(Files.exists(javaOutputDir.resolve("acme/telemetry/packet/Packet.java")));
    assertTrue(Files.exists(cppOutputDir.resolve("acme/telemetry/Header.hpp")));
    assertTrue(Files.exists(cppOutputDir.resolve("acme/telemetry/packet/Packet.cpp")));
  }

  /**
   * Runs the native CLI executable and captures combined output.
   *
   * @param args command arguments passed to the native executable
   * @return process result with exit code and combined output
   */
  private static CommandResult runNativeCommand(String... args) {
    Path nativeExecutablePath = requireNativeExecutablePath();
    List<String> command = new ArrayList<>();
    command.add(nativeExecutablePath.toString());
    command.addAll(List.of(args));

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(repositoryRoot().toFile());
    processBuilder.redirectErrorStream(true);

    try {
      Process process = processBuilder.start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      return new CommandResult(exitCode, output);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to run native CLI command: " + String.join(" ", command), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while running native CLI command: " + String.join(" ", command), exception);
    }
  }

  /**
   * Resolves one fixture resource path to absolute filesystem text.
   *
   * @param resourcePath fixture path under test resources
   * @return absolute fixture path text
   */
  private static String fixturePath(String resourcePath) {
    return TestSupport.resourcePath(resourcePath).toString();
  }

  /**
   * Resolves the repository root path from the known XSD location.
   *
   * @return repository root directory
   */
  private static Path repositoryRoot() {
    Path xsdPath = TestSupport.repositoryXsdPath();
    Path xsdDirectory = Objects.requireNonNull(xsdPath.getParent(), "xsdDirectory");
    Path specDirectory = Objects.requireNonNull(xsdDirectory.getParent(), "specDirectory");
    return Objects.requireNonNull(specDirectory.getParent(), "repositoryRoot");
  }

  /**
   * Resolves the native executable path passed by Gradle task configuration.
   *
   * @return absolute executable path
   */
  private static Path requireNativeExecutablePath() {
    String configuredPath = System.getProperty(NATIVE_EXECUTABLE_PROPERTY, "").trim();
    assertTrue(
        !configuredPath.isEmpty(),
        "Native executable path must be set using system property " + NATIVE_EXECUTABLE_PROPERTY);

    Path nativeExecutablePath = Path.of(configuredPath).toAbsolutePath().normalize();
    assertTrue(
        Files.exists(nativeExecutablePath),
        "Native executable was not found at " + nativeExecutablePath);
    assertTrue(
        Files.isRegularFile(nativeExecutablePath),
        "Native executable path must point to a file: " + nativeExecutablePath);
    return nativeExecutablePath;
  }

  /**
   * Captures one native process execution result.
   *
   * @param exitCode process exit code
   * @param output combined stdout/stderr text
   */
  private record CommandResult(int exitCode, String output) {
    /** Creates one native-process command result. */
    private CommandResult {}
  }
}
