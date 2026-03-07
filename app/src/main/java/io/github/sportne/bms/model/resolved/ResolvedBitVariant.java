package io.github.sportne.bms.model.resolved;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Resolved representation of one named value inside a bit segment.
 *
 * @param name variant name
 * @param value integer value for this variant
 * @param comment human-readable description
 */
public record ResolvedBitVariant(String name, BigInteger value, String comment) {
  /**
   * Creates a resolved bit-segment variant.
   *
   * @param name variant name
   * @param value integer value for the variant
   * @param comment human-readable description
   */
  public ResolvedBitVariant {
    name = Objects.requireNonNull(name, "name");
    value = Objects.requireNonNull(value, "value");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
