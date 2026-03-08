package io.github.sportne.bms.semantic;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.util.BmsException;

/**
 * Shared helper methods for semantic resolver tests.
 *
 * <p>Keeping common assertions here makes each test class focus on one semantic feature slice.
 */
final class SemanticResolverTestSupport {

  /** Utility class; not instantiable. */
  private SemanticResolverTestSupport() {}

  /**
   * Resolves a parsed schema and asserts that resolution fails.
   *
   * @param parsedSchema parsed schema input
   * @return exception thrown by semantic resolution
   */
  static BmsException assertResolutionFails(ParsedSchema parsedSchema) {
    return assertThrows(
        BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));
  }

  /**
   * Asserts that diagnostics contain a specific diagnostic code.
   *
   * @param exception semantic resolver exception
   * @param code expected diagnostic code
   */
  static void assertHasDiagnostic(BmsException exception, String code) {
    assertTrue(
        exception.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(code)));
  }

  /**
   * Asserts that diagnostics contain a specific code plus a message fragment.
   *
   * @param exception semantic resolver exception
   * @param code expected diagnostic code
   * @param messageFragment expected fragment in diagnostic message text
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
