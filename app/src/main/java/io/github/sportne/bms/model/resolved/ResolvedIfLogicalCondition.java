package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.IfLogicalOperator;
import java.util.Objects;

/**
 * Logical binary node in one resolved {@code if} condition tree.
 *
 * @param left left child condition
 * @param operator logical operator between children
 * @param right right child condition
 */
public record ResolvedIfLogicalCondition(
    ResolvedIfCondition left, IfLogicalOperator operator, ResolvedIfCondition right)
    implements ResolvedIfCondition {

  /**
   * Creates one resolved logical-condition node.
   *
   * @param left left child condition
   * @param operator logical operator between children
   * @param right right child condition
   */
  public ResolvedIfLogicalCondition {
    left = Objects.requireNonNull(left, "left");
    operator = Objects.requireNonNull(operator, "operator");
    right = Objects.requireNonNull(right, "right");
  }
}
