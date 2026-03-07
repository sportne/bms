package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to a reusable scaled-int definition declared at schema scope.
 *
 * @param scaledIntTypeName resolved scaled-int type name
 */
public record ScaledIntTypeRef(String scaledIntTypeName) implements ResolvedTypeRef {
  /**
   * Creates a reference to a reusable scaled-int type.
   *
   * @param scaledIntTypeName reusable scaled-int type name
   */
  public ScaledIntTypeRef {
    scaledIntTypeName = Objects.requireNonNull(scaledIntTypeName, "scaledIntTypeName");
  }
}
