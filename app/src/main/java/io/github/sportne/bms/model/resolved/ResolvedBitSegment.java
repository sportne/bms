package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

/** Resolved representation of one named segment inside a bit field. */
public record ResolvedBitSegment(
    String name, int fromBit, int toBit, String comment, List<ResolvedBitVariant> variants) {

  public ResolvedBitSegment {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
  }
}
