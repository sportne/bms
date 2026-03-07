package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/** Parsed representation of one {@code flag} inside a {@code bitField}. */
public record ParsedBitFlag(String name, int position, String comment) {

  public ParsedBitFlag {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
