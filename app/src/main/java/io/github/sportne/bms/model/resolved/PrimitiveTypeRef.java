package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to one built-in primitive integer type.
 *
 * @param primitiveType resolved primitive type
 */
public record PrimitiveTypeRef(PrimitiveType primitiveType) implements ResolvedTypeRef {
  /**
   * Creates a reference to one primitive wire type.
   *
   * @param primitiveType primitive wire type
   */
  public PrimitiveTypeRef {
    primitiveType = Objects.requireNonNull(primitiveType, "primitiveType");
  }
}
