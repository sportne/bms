package io.github.sportne.bms.parse;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.util.BmsException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared helper methods for parser contract tests.
 *
 * <p>This class keeps repeated setup and diagnostic assertions in one place so each test file can
 * focus on one behavior area.
 */
final class SpecParserTestSupport {

  /** Utility class; not instantiable. */
  private SpecParserTestSupport() {}

  /**
   * Parses an in-memory XML string by writing it to a temporary file and expecting parser failure.
   *
   * @param parser parser under test
   * @param xml XML string to write and parse
   * @return raised parser exception
   * @throws Exception when temporary file IO fails
   */
  static BmsException parseInlineXmlExpectingFailure(SpecParser parser, String xml)
      throws Exception {
    Path specPath = writeTempSpec(xml);
    try {
      return assertThrows(BmsException.class, () -> parser.parse(specPath));
    } finally {
      Files.deleteIfExists(specPath);
    }
  }

  /**
   * Writes XML text to a temporary file and returns that path.
   *
   * @param xml XML string to write
   * @return path to a temporary XML file
   * @throws Exception when temporary file IO fails
   */
  static Path writeTempSpec(String xml) throws Exception {
    Path tempFile = Files.createTempFile("bms-parser-", ".xml");
    Files.writeString(tempFile, xml, StandardCharsets.UTF_8);
    return tempFile;
  }

  /**
   * Asserts that at least one diagnostic with the provided code exists.
   *
   * @param exception parser exception containing diagnostics
   * @param code expected diagnostic code
   */
  static void assertHasDiagnostic(BmsException exception, String code) {
    assertTrue(
        exception.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(code)));
  }

  /**
   * Asserts that at least one diagnostic with the provided code and message fragment exists.
   *
   * @param exception parser exception containing diagnostics
   * @param code expected diagnostic code
   * @param messageFragment expected message fragment
   */
  static void assertHasDiagnosticContaining(
      BmsException exception, String code, String messageFragment) {
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic.code().equals(code)
                        && diagnostic.message().contains(messageFragment)));
  }
}
