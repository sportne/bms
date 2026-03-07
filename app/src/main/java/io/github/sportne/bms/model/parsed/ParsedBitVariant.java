package io.github.sportne.bms.model.parsed;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Parsed representation of one allowed value inside a bit segment.
 *
 * @param name variant name
 * @param value integer value for this variant
 * @param comment human-readable description
 */
public record ParsedBitVariant(String name, BigInteger value, String comment) {
  /**
   * Creates a parsed segment-variant entry.
   *
   * @param name variant name
   * @param value integer value for the variant
   * @param comment human-readable description
   */
  public ParsedBitVariant {
    name = Objects.requireNonNull(name, "name");
    value = Objects.requireNonNull(value, "value");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
