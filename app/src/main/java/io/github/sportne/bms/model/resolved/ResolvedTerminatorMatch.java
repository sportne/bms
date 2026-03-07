package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Final resolved node in a terminator path.
 *
 * @param value literal value that marks termination
 */
public record ResolvedTerminatorMatch(String value) implements ResolvedTerminatorNode {

  /**
   * Creates a resolved terminator-match node.
   *
   * @param value literal value that marks termination
   */
  public ResolvedTerminatorMatch {
    value = Objects.requireNonNull(value, "value");
  }
}
