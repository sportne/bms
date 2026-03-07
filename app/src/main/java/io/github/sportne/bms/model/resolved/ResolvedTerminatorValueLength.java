package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Length strategy that stops when a terminator literal is found.
 *
 * @param value literal terminator value from XML
 */
public record ResolvedTerminatorValueLength(String value) implements ResolvedLengthMode {

  /**
   * Creates a resolved terminator-value length strategy.
   *
   * @param value literal terminator value from XML
   */
  public ResolvedTerminatorValueLength {
    value = Objects.requireNonNull(value, "value");
  }
}
