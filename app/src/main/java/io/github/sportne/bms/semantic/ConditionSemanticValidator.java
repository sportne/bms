package io.github.sportne.bms.semantic;

import io.github.sportne.bms.codegen.common.PrimitiveNumericRules;
import io.github.sportne.bms.model.IfLogicalOperator;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedIfComparison;
import io.github.sportne.bms.model.resolved.ResolvedIfCondition;
import io.github.sportne.bms.model.resolved.ResolvedIfLogicalCondition;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.util.List;
import java.util.Map;

/**
 * Validates and resolves textual {@code if@test} expressions.
 *
 * <p>This helper keeps condition-specific semantic rules separate from the broader schema
 * resolution flow.
 */
final class ConditionSemanticValidator {
  /** Creates a utility-only helper class. */
  private ConditionSemanticValidator() {}

  /**
   * Parses and validates one textual {@code if@test} condition.
   *
   * @param messageName parent message name used in diagnostics
   * @param conditionText raw condition text
   * @param primitiveFieldByName primitive field lookup map
   * @param sourcePath source path used in diagnostics
   * @param diagnostics destination diagnostics list
   * @return resolved condition tree, or {@code null} when invalid
   */
  static ResolvedIfCondition resolveIfCondition(
      String messageName,
      String conditionText,
      Map<String, PrimitiveType> primitiveFieldByName,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    IfConditionExpressionParser.ParsedCondition parsedCondition;
    try {
      parsedCondition = IfConditionExpressionParser.parse(conditionText);
    } catch (IfConditionExpressionParser.IfConditionParseException exception) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_IF_TEST",
              "if@test in message " + messageName + " is invalid: " + exception.getMessage(),
              sourcePath));
      return null;
    }
    return resolveIfConditionNode(
        messageName, conditionText, parsedCondition, primitiveFieldByName, sourcePath, diagnostics);
  }

  /**
   * Resolves one parsed condition node into a resolved node and validates semantic rules.
   *
   * @param messageName parent message name used in diagnostics
   * @param conditionText raw condition text
   * @param parsedCondition parsed condition node
   * @param primitiveFieldByName primitive field lookup map
   * @param sourcePath source path used in diagnostics
   * @param diagnostics destination diagnostics list
   * @return resolved condition node, or {@code null} when invalid
   */
  private static ResolvedIfCondition resolveIfConditionNode(
      String messageName,
      String conditionText,
      IfConditionExpressionParser.ParsedCondition parsedCondition,
      Map<String, PrimitiveType> primitiveFieldByName,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    if (parsedCondition instanceof IfConditionExpressionParser.ParsedComparisonCondition parsed) {
      PrimitiveType primitiveType = primitiveFieldByName.get(parsed.fieldName());
      if (primitiveType == null) {
        diagnostics.add(
            error(
                "SEMANTIC_INVALID_IF_TEST",
                "if@test in message "
                    + messageName
                    + " references unknown primitive field: "
                    + parsed.fieldName()
                    + ". Condition: "
                    + conditionText,
                sourcePath));
        return null;
      }
      if (!PrimitiveNumericRules.fitsPrimitiveRange(parsed.literal(), primitiveType)) {
        diagnostics.add(
            error(
                "SEMANTIC_INVALID_IF_TEST",
                "if@test in message "
                    + messageName
                    + " uses literal out of range for field "
                    + parsed.fieldName()
                    + ": "
                    + parsed.literal(),
                sourcePath));
        return null;
      }
      return new ResolvedIfComparison(
          parsed.fieldName(), primitiveType, parsed.operator(), parsed.literal());
    }
    if (parsedCondition
        instanceof IfConditionExpressionParser.ParsedLogicalCondition parsedLogical) {
      ResolvedIfCondition left =
          resolveIfConditionNode(
              messageName,
              conditionText,
              parsedLogical.left(),
              primitiveFieldByName,
              sourcePath,
              diagnostics);
      ResolvedIfCondition right =
          resolveIfConditionNode(
              messageName,
              conditionText,
              parsedLogical.right(),
              primitiveFieldByName,
              sourcePath,
              diagnostics);
      if (left == null || right == null) {
        return null;
      }
      IfLogicalOperator operator = parsedLogical.operator();
      return new ResolvedIfLogicalCondition(left, operator, right);
    }
    throw new IllegalStateException("Unsupported parsed if-condition node: " + parsedCondition);
  }

  /**
   * Builds one semantic error diagnostic.
   *
   * @param code stable diagnostic code
   * @param message human-readable diagnostic message
   * @param sourcePath source path used in diagnostics
   * @return error diagnostic value
   */
  private static Diagnostic error(String code, String message, String sourcePath) {
    return new Diagnostic(DiagnosticSeverity.ERROR, code, message, sourcePath, -1, -1);
  }
}
