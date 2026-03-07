package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to a reusable float definition declared at schema scope.
 *
 * @param floatTypeName resolved float type name
 */
public record FloatTypeRef(String floatTypeName) implements ResolvedTypeRef {
  /**
   * Creates a reference to a reusable float type.
   *
   * @param floatTypeName reusable float type name
   */
  public FloatTypeRef {
    floatTypeName = Objects.requireNonNull(floatTypeName, "floatTypeName");
  }
}
