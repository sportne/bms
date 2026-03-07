package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Length strategy that reads element count from another field.
 *
 * @param ref referenced field name
 */
public record ResolvedCountFieldLength(String ref) implements ResolvedLengthMode {

  /**
   * Creates a resolved count-field length strategy.
   *
   * @param ref referenced field name
   */
  public ResolvedCountFieldLength {
    ref = Objects.requireNonNull(ref, "ref");
  }
}
