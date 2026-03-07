package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

/**
 * Resolved representation of one named segment inside a bit field.
 *
 * @param name segment name
 * @param fromBit starting bit index (inclusive)
 * @param toBit ending bit index (inclusive)
 * @param comment human-readable description
 * @param variants resolved variants for this segment
 */
public record ResolvedBitSegment(
    String name, int fromBit, int toBit, String comment, List<ResolvedBitVariant> variants) {
  /**
   * Creates a resolved bit segment.
   *
   * @param name segment name
   * @param fromBit starting bit index (inclusive)
   * @param toBit ending bit index (inclusive)
   * @param comment human-readable description
   * @param variants resolved segment variants
   */
  public ResolvedBitSegment {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
  }
}
