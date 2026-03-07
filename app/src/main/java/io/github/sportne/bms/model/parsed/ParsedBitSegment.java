package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of one named bit range inside a {@code bitField}.
 *
 * @param name segment name
 * @param fromBit starting bit index (inclusive)
 * @param toBit ending bit index (inclusive)
 * @param comment human-readable description
 * @param variants allowed named values for this range
 */
public record ParsedBitSegment(
    String name, int fromBit, int toBit, String comment, List<ParsedBitVariant> variants) {
  /**
   * Creates a parsed bit-segment entry.
   *
   * @param name segment name
   * @param fromBit starting bit index (inclusive)
   * @param toBit ending bit index (inclusive)
   * @param comment human-readable description
   * @param variants allowed named values
   */
  public ParsedBitSegment {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
  }
}
