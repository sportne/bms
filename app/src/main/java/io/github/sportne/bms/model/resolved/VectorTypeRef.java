package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to a reusable vector definition declared at schema scope.
 *
 * @param vectorTypeName resolved vector type name
 */
public record VectorTypeRef(String vectorTypeName) implements ResolvedTypeRef {

  /**
   * Creates a reference to a reusable vector type.
   *
   * @param vectorTypeName reusable vector type name
   */
  public VectorTypeRef {
    vectorTypeName = Objects.requireNonNull(vectorTypeName, "vectorTypeName");
  }
}
