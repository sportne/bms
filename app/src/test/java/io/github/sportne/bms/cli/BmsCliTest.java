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

/**
 * Contract tests for CLI behavior.
 *
 * <p>These tests focus on user-visible rules:
 *
 * <ul>
 *   <li>which exit code is returned
 *   <li>whether output is sent to stdout or stderr
 *   <li>whether expected files are generated
 * </ul>
 */
class BmsCliTest {

  @TempDir Path tempDir;

  @Test
  /** Contract: `validate` succeeds for a known-good foundation spec. */
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
  /** Contract: `validate` also succeeds for the current numeric front-end slice. */
  void validateCommandReturnsSuccessForNumericSliceSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "validate", TestSupport.resourcePath("specs/numeric-slice-valid.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertTrue(stdoutBuffer.toString(StandardCharsets.UTF_8).contains("Validation succeeded"));
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  /** Contract: `validate` succeeds for the collection + terminator front-end slice. */
  void validateCommandReturnsSuccessForCollectionSliceSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "validate", TestSupport.resourcePath("specs/collections-slice-valid.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertTrue(stdoutBuffer.toString(StandardCharsets.UTF_8).contains("Validation succeeded"));
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  /** Contract: `validate` succeeds for milestone-03 frontend constructs. */
  void validateCommandReturnsSuccessForMilestoneThreeSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "validate", TestSupport.resourcePath("specs/milestone-03-valid.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertTrue(stdoutBuffer.toString(StandardCharsets.UTF_8).contains("Validation succeeded"));
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  /** Contract: `validate` succeeds for staged conditional fixture (`varString` + `pad`). */
  void validateCommandReturnsSuccessForStagedConditionalSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "validate", TestSupport.resourcePath("specs/varstring-pad-slice-valid.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertTrue(stdoutBuffer.toString(StandardCharsets.UTF_8).contains("Validation succeeded"));
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  /** Contract: `validate` succeeds for full Java-conditional backend fixture input. */
  void validateCommandReturnsSuccessForConditionalBackendSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "validate", TestSupport.resourcePath("specs/conditional-backend-valid.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertTrue(stdoutBuffer.toString(StandardCharsets.UTF_8).contains("Validation succeeded"));
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
  }

  /** Contract: `validate` succeeds for relational `if@test` fixture input. */
  @Test
  void validateCommandReturnsSuccessForRelationalConditionalSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "validate",
              TestSupport.resourcePath("specs/conditional-if-relational-valid.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertTrue(stdoutBuffer.toString(StandardCharsets.UTF_8).contains("Validation succeeded"));
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  /** Contract: invalid specs produce exit code 1 and include an XSD diagnostic. */
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
  /** Contract: Java generation succeeds for the numeric backend slice. */
  void generateCommandReturnsSuccessForNumericSliceSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/numeric-slice-valid.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
    assertTrue(Files.exists(javaOutputDir.resolve("acme/telemetry/numeric/TelemetryFrame.java")));
  }

  @Test
  /** Contract: Java generation succeeds for the collection backend slice. */
  void generateCommandReturnsSuccessForCollectionSliceSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-collections");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/collections-slice-valid.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
    assertTrue(
        Files.exists(javaOutputDir.resolve("acme/telemetry/collections/CollectionFrame.java")));
  }

  @Test
  /** Contract: Java generation succeeds for checksum/if/nested conditional backend fixture. */
  void generateCommandReturnsSuccessForConditionalBackendFixture() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-conditional-backend");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/conditional-backend-valid.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
    assertTrue(
        Files.exists(
            javaOutputDir.resolve(
                "acme/telemetry/conditional/backend/ConditionalBackendFrame.java")));
  }

  /** Contract: Java generation succeeds for crc64 checksum fixture. */
  @Test
  void generateCommandReturnsSuccessForCrc64ChecksumFixture() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-checksum-crc64");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/checksum-crc64-valid.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
    assertTrue(
        Files.exists(
            javaOutputDir.resolve(
                "acme/telemetry/conditional/algorithms/ChecksumCrc64Frame.java")));
  }

  /** Contract: Java generation succeeds for sha256 checksum fixture. */
  @Test
  void generateCommandReturnsSuccessForSha256ChecksumFixture() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-checksum-sha256");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/checksum-sha256-valid.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
    assertTrue(
        Files.exists(
            javaOutputDir.resolve(
                "acme/telemetry/conditional/algorithms/ChecksumSha256Frame.java")));
  }

  /** Contract: Java generation succeeds for relational `if@test` operators. */
  @Test
  void generateCommandReturnsSuccessForRelationalConditionalFixture() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-relational-if");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/conditional-if-relational-valid.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
    assertTrue(
        Files.exists(
            javaOutputDir.resolve(
                "acme/telemetry/conditional/relational/ConditionalRelationalFrame.java")));
  }

  @Test
  /** Contract: unsupported if-expression syntax still fails clearly in Java generation. */
  void generateCommandReturnsSpecErrorForUnsupportedIfTestExpression() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-conditional-unsupported-if");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/conditional-if-unsupported-test.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(1, exitCode);
    assertTrue(
        stderrBuffer
            .toString(StandardCharsets.UTF_8)
            .contains("GENERATOR_JAVA_UNSUPPORTED_MEMBER"));
  }

  @Test
  /** Contract: invalid checksum ranges fail clearly during Java generation. */
  void generateCommandReturnsSpecErrorForInvalidChecksumRange() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-invalid-checksum-range");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/checksum-invalid-range.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(1, exitCode);
    assertTrue(
        stderrBuffer
            .toString(StandardCharsets.UTF_8)
            .contains("GENERATOR_JAVA_UNSUPPORTED_MEMBER"));
  }

  /** Contract: invalid crc64 checksum ranges fail clearly during Java generation. */
  @Test
  void generateCommandReturnsSpecErrorForInvalidCrc64ChecksumRange() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-invalid-checksum-range-crc64");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/checksum-crc64-invalid-range.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(1, exitCode);
    assertTrue(
        stderrBuffer
            .toString(StandardCharsets.UTF_8)
            .contains("GENERATOR_JAVA_UNSUPPORTED_MEMBER"));
  }

  /** Contract: invalid sha256 checksum ranges fail clearly during Java generation. */
  @Test
  void generateCommandReturnsSpecErrorForInvalidSha256ChecksumRange() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-invalid-checksum-range-sha256");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/checksum-sha256-invalid-range.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(1, exitCode);
    assertTrue(
        stderrBuffer
            .toString(StandardCharsets.UTF_8)
            .contains("GENERATOR_JAVA_UNSUPPORTED_MEMBER"));
  }

  /** Contract: out-of-range relational if@test literals fail clearly during generation. */
  @Test
  void generateCommandReturnsSpecErrorForOutOfRangeRelationalIfLiterals() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-invalid-relational-if");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/conditional-if-relational-invalid.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(1, exitCode);
    assertTrue(
        stderrBuffer
            .toString(StandardCharsets.UTF_8)
            .contains("GENERATOR_JAVA_UNSUPPORTED_MEMBER"));
  }

  @Test
  /** Contract: Java generation succeeds for staged conditional members (`varString` and `pad`). */
  void generateCommandReturnsSuccessForStagedConditionalSliceSpec() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path javaOutputDir = tempDir.resolve("java-conditional-staged");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/varstring-pad-slice-valid.xml").toString(),
              "--java",
              javaOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(0, exitCode);
    assertEquals("", stderrBuffer.toString(StandardCharsets.UTF_8));
    assertTrue(
        Files.exists(javaOutputDir.resolve("acme/telemetry/conditional/ConditionalFrame.java")));
  }

  @Test
  /** Contract: C++ generation also fails clearly for unsupported milestone-03 members. */
  void generateCommandReturnsSpecErrorForUnsupportedMilestoneThreeMembersInCpp() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    Path cppOutputDir = tempDir.resolve("cpp-milestone-three");

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/milestone-03-valid.xml").toString(),
              "--cpp",
              cppOutputDir.toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(1, exitCode);
    assertTrue(
        stderrBuffer.toString(StandardCharsets.UTF_8).contains("GENERATOR_CPP_UNSUPPORTED_MEMBER"));
    assertTrue(
        stderrBuffer
            .toString(StandardCharsets.UTF_8)
            .contains("GENERATOR_CPP_UNSUPPORTED_TYPE_REF"));
  }

  @Test
  /** Contract: `generate` writes deterministic Java and C++ outputs for the foundation spec. */
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
  /** Contract: running the CLI with no arguments is a usage error (exit code 2). */
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
  /** Contract: unknown commands are reported as usage errors. */
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
  /** Contract: unknown options for `generate` are reported as usage errors. */
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
  /** Contract: `validate` requires exactly one spec path. */
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
  /** Contract: `generate` options that need a value fail when that value is missing. */
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

  @Test
  /** Contract: `generate` requires at least one target option after the spec path. */
  void generateCommandReturnsUsageErrorWhenTooFewArgumentsAreProvided() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "generate", TestSupport.resourcePath("specs/valid-foundation.xml").toString()
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(2, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("generate requires"));
  }

  @Test
  /** Contract: `generate` reports a usage error when `--cpp` is missing its value. */
  void generateCommandReturnsUsageErrorWhenCppValueIsMissing() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {
              "generate",
              TestSupport.resourcePath("specs/valid-foundation.xml").toString(),
              "--java",
              tempDir.resolve("java").toString(),
              "--cpp"
            },
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(2, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("missing path after --cpp"));
  }

  @Test
  /** Contract: unexpected runtime argument parsing errors map to exit code 3. */
  void validateCommandReturnsInternalErrorForInvalidPathLiteral() {
    BmsCli cli = new BmsCli();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

    int exitCode =
        cli.run(
            new String[] {"validate", "\u0000bad-path"},
            new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8),
            new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8));

    assertEquals(3, exitCode);
    assertTrue(stderrBuffer.toString(StandardCharsets.UTF_8).contains("Internal error"));
  }
}
