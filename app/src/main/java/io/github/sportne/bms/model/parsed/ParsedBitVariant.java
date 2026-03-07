package io.github.sportne.bms.model.parsed;

import java.math.BigInteger;
import java.util.Objects;

/** Parsed representation of one allowed value inside a bit segment. */
public record ParsedBitVariant(String name, BigInteger value, String comment) {

  public ParsedBitVariant {
    name = Objects.requireNonNull(name, "name");
    value = Objects.requireNonNull(value, "value");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
