package io.github.sportne.bms.codegen.common;

import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorMatch;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorNode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;

/** Shared helpers for collection and varString length modes. */
public final class LengthModeRules {
  /** Creates a utility-only helper class. */
  private LengthModeRules() {}

  /**
   * Returns the terminal literal for one terminator-based length mode.
   *
   * @param lengthMode length mode that must be terminator-based
   * @return terminal terminator literal
   * @throws IllegalStateException when the mode is not terminator-based
   */
  public static String terminatorLiteral(ResolvedLengthMode lengthMode) {
    if (lengthMode instanceof ResolvedTerminatorValueLength resolvedTerminatorValueLength) {
      return resolvedTerminatorValueLength.value();
    }
    if (lengthMode instanceof ResolvedTerminatorField resolvedTerminatorField) {
      return terminatorLiteral(resolvedTerminatorField.next());
    }
    throw new IllegalStateException("Length mode is not terminator-based: " + lengthMode);
  }

  /**
   * Returns the terminal literal for one recursive terminator path.
   *
   * @param terminatorNode terminator node to inspect
   * @return terminal terminator literal, or {@code null} when a terminal match is missing
   * @throws IllegalStateException when the node type is unknown
   */
  public static String terminatorLiteral(ResolvedTerminatorNode terminatorNode) {
    if (terminatorNode == null) {
      return null;
    }
    if (terminatorNode instanceof ResolvedTerminatorMatch resolvedTerminatorMatch) {
      return resolvedTerminatorMatch.value();
    }
    if (terminatorNode instanceof ResolvedTerminatorField resolvedTerminatorField) {
      return terminatorLiteral(resolvedTerminatorField.next());
    }
    throw new IllegalStateException("Unknown terminator node: " + terminatorNode);
  }
}
