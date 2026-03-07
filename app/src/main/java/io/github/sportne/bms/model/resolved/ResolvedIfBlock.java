package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

/**
 * Resolved representation of a conditional {@code if} block.
 *
 * @param test condition expression text
 * @param members resolved members inside the conditional block, in declaration order
 */
public record ResolvedIfBlock(String test, List<ResolvedMessageMember> members)
    implements ResolvedMessageMember {

  /**
   * Creates a resolved conditional block.
   *
   * @param test condition expression text
   * @param members resolved members in declaration order
   */
  public ResolvedIfBlock {
    test = Objects.requireNonNull(test, "test");
    members = List.copyOf(Objects.requireNonNull(members, "members"));
  }
}
