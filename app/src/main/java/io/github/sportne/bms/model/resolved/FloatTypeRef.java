package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/** Type reference to a reusable float definition declared at schema scope. */
public record FloatTypeRef(String floatTypeName) implements ResolvedTypeRef {
  public FloatTypeRef {
    floatTypeName = Objects.requireNonNull(floatTypeName, "floatTypeName");
  }
}
