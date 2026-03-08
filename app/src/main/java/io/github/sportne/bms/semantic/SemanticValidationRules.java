package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import io.github.sportne.bms.util.Diagnostics;
import java.util.List;
import java.util.regex.Pattern;

/** Shared semantic validation helpers used across resolver collaborators. */
final class SemanticValidationRules {
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern NAMESPACE_PATTERN =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

  /** Prevents instantiation of this static helper class. */
  private SemanticValidationRules() {}

  /**
   * Validates that a value matches identifier syntax.
   *
   * @param value value being checked
   * @param code diagnostic code to use on failure
   * @param messagePrefix message prefix used on failure
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  static void validateIdentifier(
      String value,
      String code,
      String messagePrefix,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    if (!IDENTIFIER_PATTERN.matcher(value).matches()) {
      diagnostics.add(error(code, messagePrefix + value, sourcePath));
    }
  }

  /**
   * Validates that a namespace is non-blank and dot-delimited identifier segments.
   *
   * @param namespace namespace value to validate
   * @param attributeName attribute label used in diagnostics
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  static void validateNamespace(
      String namespace, String attributeName, String sourcePath, List<Diagnostic> diagnostics) {
    if (namespace == null || namespace.isBlank()) {
      diagnostics.add(
          error("SEMANTIC_INVALID_NAMESPACE", attributeName + " must not be blank.", sourcePath));
      return;
    }
    if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_NAMESPACE",
              attributeName + " must be dot-delimited identifiers. Received: " + namespace,
              sourcePath));
    }
  }

  /**
   * Validates scale rules for float definitions.
   *
   * @param parsedFloat parsed float object
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  static void validateFloatScaleRules(
      ParsedFloat parsedFloat, String sourcePath, List<Diagnostic> diagnostics) {
    if (parsedFloat.encoding() == io.github.sportne.bms.model.FloatEncoding.SCALED
        && parsedFloat.scale() == null) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_FLOAT_SCALE",
              "Float " + parsedFloat.name() + " uses scaled encoding but has no scale value.",
              sourcePath));
    }
    if (parsedFloat.encoding() == io.github.sportne.bms.model.FloatEncoding.IEEE754
        && parsedFloat.scale() != null) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_FLOAT_SCALE",
              "Float " + parsedFloat.name() + " uses ieee754 encoding and must not define scale.",
              sourcePath));
    }
  }

  /**
   * Throws a semantic-validation exception when any error diagnostics were collected.
   *
   * @param diagnostics semantic diagnostics list
   * @throws BmsException if one or more error diagnostics are present
   */
  static void throwIfDiagnosticsContainErrors(List<Diagnostic> diagnostics) throws BmsException {
    if (Diagnostics.hasErrors(diagnostics)) {
      throw new BmsException("Semantic validation failed.", diagnostics);
    }
  }

  /**
   * Builds one error-level diagnostic with unknown line/column.
   *
   * @param code stable diagnostic code
   * @param message human-readable diagnostic message
   * @param sourcePath source path used in diagnostics
   * @return error diagnostic
   */
  static Diagnostic error(String code, String message, String sourcePath) {
    return new Diagnostic(DiagnosticSeverity.ERROR, code, message, sourcePath, -1, -1);
  }
}
