package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.IfComparisonOperator;
import java.math.BigInteger;
import java.util.Objects;

/**
 * One resolved primitive comparison inside an {@code if} condition.
 *
 * @param fieldName referenced primitive field name
 * @param fieldType primitive type of the referenced field
 * @param operator comparison operator
 * @param literal parsed numeric literal value
 */
public record ResolvedIfComparison(
    String fieldName, PrimitiveType fieldType, IfComparisonOperator operator, BigInteger literal)
    implements ResolvedIfCondition {

  /**
   * Creates one resolved comparison node.
   *
   * @param fieldName referenced primitive field name
   * @param fieldType primitive type of the referenced field
   * @param operator comparison operator
   * @param literal parsed numeric literal value
   */
  public ResolvedIfComparison {
    fieldName = Objects.requireNonNull(fieldName, "fieldName");
    fieldType = Objects.requireNonNull(fieldType, "fieldType");
    operator = Objects.requireNonNull(operator, "operator");
    literal = Objects.requireNonNull(literal, "literal");
  }
}
