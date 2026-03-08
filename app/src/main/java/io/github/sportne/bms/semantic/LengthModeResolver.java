package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorMatch;
import io.github.sportne.bms.model.parsed.ParsedTerminatorNode;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorMatch;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorNode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.util.Diagnostic;
import java.util.List;
import java.util.Set;

/** Resolves parsed length-mode objects and enforces count/terminator rules. */
final class LengthModeResolver {
  /** Prevents instantiation of this static helper class. */
  private LengthModeResolver() {}

  /**
   * Resolves one parsed length mode and enforces count-field/terminator rules.
   *
   * @param parsedLengthMode parsed mode object
   * @param ownerContext owning context used in diagnostics
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @param knownCountFields known earlier primitive field names (message-level only)
   * @param strictCountFieldRef whether count-field references must resolve to earlier primitive
   *     fields
   * @param allowTerminatorField whether terminator-field mode is valid in this context
   * @return resolved length mode, or {@code null} when invalid
   */
  static ResolvedLengthMode resolveLengthMode(
      ParsedLengthMode parsedLengthMode,
      String ownerContext,
      String sourcePath,
      List<Diagnostic> diagnostics,
      Set<String> knownCountFields,
      boolean strictCountFieldRef,
      boolean allowTerminatorField) {
    if (parsedLengthMode instanceof ParsedCountFieldLength parsedCountFieldLength) {
      SemanticValidationRules.validateIdentifier(
          parsedCountFieldLength.ref(),
          "SEMANTIC_INVALID_COUNT_FIELD_REF",
          "countField ref must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (strictCountFieldRef && !knownCountFields.contains(parsedCountFieldLength.ref())) {
        diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_INVALID_COUNT_FIELD_REF",
                "countField ref in "
                    + ownerContext
                    + " must point to an earlier scalar integer field: "
                    + parsedCountFieldLength.ref(),
                sourcePath));
      }
      return new ResolvedCountFieldLength(parsedCountFieldLength.ref());
    }

    if (parsedLengthMode instanceof ParsedTerminatorValueLength parsedTerminatorValueLength) {
      return new ResolvedTerminatorValueLength(parsedTerminatorValueLength.value());
    }

    if (!allowTerminatorField) {
      diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_INVALID_LENGTH_MODE",
              ownerContext + " does not allow terminatorField length mode.",
              sourcePath));
      return null;
    }

    ParsedTerminatorField parsedTerminatorField = (ParsedTerminatorField) parsedLengthMode;
    if (!terminatorPathEndsInMatch(parsedTerminatorField)) {
      diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_INVALID_TERMINATOR_FIELD_PATH",
              "terminatorField path in " + ownerContext + " must end in terminatorMatch.",
              sourcePath));
    }
    return resolveTerminatorField(parsedTerminatorField, sourcePath, diagnostics);
  }

  /**
   * Resolves one recursive parsed terminator-field node.
   *
   * @param parsedTerminatorField parsed terminator-field node
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @return resolved terminator-field node
   */
  private static ResolvedTerminatorField resolveTerminatorField(
      ParsedTerminatorField parsedTerminatorField,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    SemanticValidationRules.validateIdentifier(
        parsedTerminatorField.name(),
        "SEMANTIC_INVALID_TERMINATOR_FIELD_NAME",
        "terminatorField name must be a valid identifier: ",
        sourcePath,
        diagnostics);

    ParsedTerminatorNode parsedNext = parsedTerminatorField.next();
    if (parsedNext == null) {
      return new ResolvedTerminatorField(parsedTerminatorField.name(), null);
    }

    ResolvedTerminatorNode resolvedNext =
        parsedNext instanceof ParsedTerminatorField parsedNestedTerminatorField
            ? resolveTerminatorField(parsedNestedTerminatorField, sourcePath, diagnostics)
            : new ResolvedTerminatorMatch(((ParsedTerminatorMatch) parsedNext).value());
    return new ResolvedTerminatorField(parsedTerminatorField.name(), resolvedNext);
  }

  /**
   * Checks whether a parsed terminator path eventually reaches a terminator-match node.
   *
   * @param parsedTerminatorField parsed path root
   * @return {@code true} when the path ends in a match node
   */
  private static boolean terminatorPathEndsInMatch(ParsedTerminatorField parsedTerminatorField) {
    ParsedTerminatorNode next = parsedTerminatorField.next();
    if (next == null) {
      return false;
    }
    if (next instanceof ParsedTerminatorMatch) {
      return true;
    }
    return terminatorPathEndsInMatch((ParsedTerminatorField) next);
  }
}
