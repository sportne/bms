package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Length strategy that reads element count from another field.
 *
 * @param ref referenced field name
 */
public record ParsedCountFieldLength(String ref) implements ParsedLengthMode {

  /**
   * Creates a parsed count-field length strategy.
   *
   * @param ref referenced field name
   */
  public ParsedCountFieldLength {
    ref = Objects.requireNonNull(ref, "ref");
  }
}
