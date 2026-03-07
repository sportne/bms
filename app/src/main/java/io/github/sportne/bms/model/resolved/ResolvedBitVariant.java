package io.github.sportne.bms.model.resolved;

import java.math.BigInteger;
import java.util.Objects;

/** Resolved representation of one named value inside a bit segment. */
public record ResolvedBitVariant(String name, BigInteger value, String comment) {

  public ResolvedBitVariant {
    name = Objects.requireNonNull(name, "name");
    value = Objects.requireNonNull(value, "value");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
