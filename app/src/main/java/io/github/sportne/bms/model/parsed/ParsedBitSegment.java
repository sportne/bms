package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

/** Parsed representation of one named bit range inside a {@code bitField}. */
public record ParsedBitSegment(
    String name, int fromBit, int toBit, String comment, List<ParsedBitVariant> variants) {

  public ParsedBitSegment {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
  }
}
