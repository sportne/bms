package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Parsed representation of explicit byte padding.
 *
 * @param bytes number of zero/unused bytes to reserve
 * @param comment optional human-readable description
 */
public record ParsedPad(int bytes, String comment) implements ParsedMessageMember {

  /**
   * Creates a parsed pad definition.
   *
   * @param bytes number of bytes to pad
   * @param comment optional human-readable description
   */
  public ParsedPad {
    comment = Objects.requireNonNullElse(comment, "");
  }
}
