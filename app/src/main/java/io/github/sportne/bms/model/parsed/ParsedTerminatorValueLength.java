package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Length strategy that stops when a terminator literal is found.
 *
 * @param value literal terminator value from XML
 */
public record ParsedTerminatorValueLength(String value) implements ParsedLengthMode {

  /**
   * Creates a parsed terminator-value length strategy.
   *
   * @param value literal terminator value from XML
   */
  public ParsedTerminatorValueLength {
    value = Objects.requireNonNull(value, "value");
  }
}
