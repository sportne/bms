package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Recursive parsed representation of a {@code terminatorField} path step.
 *
 * @param name field name at this path level
 * @param next next nested step or match node (nullable until semantic validation)
 */
public record ParsedTerminatorField(String name, ParsedTerminatorNode next)
    implements ParsedLengthMode, ParsedTerminatorNode {

  /**
   * Creates a parsed terminator-field path step.
   *
   * @param name field name at this path level
   * @param next next nested step or match node
   */
  public ParsedTerminatorField {
    name = Objects.requireNonNull(name, "name");
  }
}
