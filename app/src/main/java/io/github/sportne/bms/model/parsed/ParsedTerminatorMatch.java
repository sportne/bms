package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Final parsed node in a terminator path.
 *
 * @param value literal value that marks termination
 */
public record ParsedTerminatorMatch(String value) implements ParsedTerminatorNode {

  /**
   * Creates a parsed terminator-match node.
   *
   * @param value literal value that marks termination
   */
  public ParsedTerminatorMatch {
    value = Objects.requireNonNull(value, "value");
  }
}
