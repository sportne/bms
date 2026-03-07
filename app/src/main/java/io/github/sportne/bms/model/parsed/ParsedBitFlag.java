package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Parsed representation of one {@code flag} inside a {@code bitField}.
 *
 * @param name flag name
 * @param position bit position inside the bitfield container
 * @param comment human-readable description
 */
public record ParsedBitFlag(String name, int position, String comment) {
  /**
   * Creates a parsed flag entry.
   *
   * @param name flag name
   * @param position bit position inside the container
   * @param comment human-readable description
   */
  public ParsedBitFlag {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
