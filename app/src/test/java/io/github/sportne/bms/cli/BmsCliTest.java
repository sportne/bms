package io.github.sportne.bms.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.testutil.TestSupport;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BmsCliTest {

  @TempDir Path tempDir;

  @Test
  void validateCommandReturnsSuccessForValidSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "validate", TestSupport.resourcePath("specs/valid-foundation.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertTrue(stdoutBuffer.toString(StandardCharsets.UTF_8).contains("Validation succeeded"));
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  void validateCommandReturnsSpecErrorForInvalidSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "validate", TestSupport.resourcePath("specs/missing-schema-namespace.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(1, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("XSD"));
  }

  @Test
  void generateCommandProducesJavaAndCppOutput() throws Exception {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java");
    Path cppOutputDir = tempDir.resolve("cpp");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/valid-foundation.xml").toString(),
              "--java",
              javaOutputDir.toString(),
              "--cpp",
              cppOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).isEmpty());

    assertTrue(Files.exists(javaOutputDir.resolve("acme/telemetry/Header.java")));
    assertTrue(Files.exists(javaOutputDir.resolve("acme/telemetry/packet/Packet.java")));
    assertTrue(Files.exists(cppOutputDir.resolve("acme/telemetry/Header.hpp")));
    assertTrue(Files.exists(cppOutputDir.resolve("acme/telemetry/packet/Packet.cpp")));
  }

  @Test
  void noArgsReturnsUsageError() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[0],
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(2, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("Usage:"));
  }

  @Test
  void unknownCommandReturnsUsageError() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {"unknown"},
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(2, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("Unknown command"));
  }

  @Test
  void generateCommandReturnsUsageErrorForUnknownOption() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/valid-foundation.xml").toString(),
              "--bogus",
              "out"
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(2, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("unknown option"));
  }

  @Test
  void validateCommandReturnsUsageErrorWhenSpecIsMissing() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {"validate"},
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(2, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("validate expects"));
  }

  @Test
  void generateCommandReturnsUsageErrorWhenOptionValueIsMissing() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/valid-foundation.xml").toString(),
              "--cpp",
              tempDir.resolve("cpp").toString(),
              "--java"
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(2, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("missing path after --java"));
  }
}
