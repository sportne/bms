package io.github.sportne.bms.model.resolved;

import java.util.Objects;

public record PrimitiveTypeRef(PrimitiveType primitiveType) implements ResolvedTypeRef {
  public PrimitiveTypeRef {
    primitiveType = Objects.requireNonNull(primitiveType, "primitiveType");
  }
}
