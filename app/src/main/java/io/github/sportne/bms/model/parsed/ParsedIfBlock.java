package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of a conditional {@code if} block.
 *
 * @param test condition expression text from XML
 * @param members members inside the conditional block, in declaration order
 */
public record ParsedIfBlock(String test, List<ParsedMessageMember> members)
    implements ParsedMessageMember {

  /**
   * Creates a parsed conditional block.
   *
   * @param test condition expression text from XML
   * @param members members in declaration order
   */
  public ParsedIfBlock {
    test = Objects.requireNonNull(test, "test");
    members = List.copyOf(Objects.requireNonNull(members, "members"));
  }
}
