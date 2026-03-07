package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Recursive resolved representation of a {@code terminatorField} path step.
 *
 * @param name field name at this path level
 * @param next next nested step or match node (nullable only when diagnostics are present)
 */
public record ResolvedTerminatorField(String name, ResolvedTerminatorNode next)
    implements ResolvedLengthMode, ResolvedTerminatorNode {

  /**
   * Creates a resolved terminator-field path step.
   *
   * @param name field name at this path level
   * @param next next nested step or match node
   */
  public ResolvedTerminatorField {
    name = Objects.requireNonNull(name, "name");
  }
}
