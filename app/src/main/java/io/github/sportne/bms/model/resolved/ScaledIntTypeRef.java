package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/** Type reference to a reusable scaled-int definition declared at schema scope. */
public record ScaledIntTypeRef(String scaledIntTypeName) implements ResolvedTypeRef {
  public ScaledIntTypeRef {
    scaledIntTypeName = Objects.requireNonNull(scaledIntTypeName, "scaledIntTypeName");
  }
}
