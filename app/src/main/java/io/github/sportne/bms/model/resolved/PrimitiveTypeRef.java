package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/** Type reference to one built-in primitive integer type. */
public record PrimitiveTypeRef(PrimitiveType primitiveType) implements ResolvedTypeRef {
  public PrimitiveTypeRef {
    primitiveType = Objects.requireNonNull(primitiveType, "primitiveType");
  }
}
