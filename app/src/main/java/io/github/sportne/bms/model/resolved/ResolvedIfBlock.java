package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

/**
 * Resolved representation of a conditional {@code if} block.
 *
 * @param condition resolved condition expression tree
 * @param members resolved members inside the conditional block, in declaration order
 */
public record ResolvedIfBlock(ResolvedIfCondition condition, List<ResolvedMessageMember> members)
    implements ResolvedMessageMember {

  /**
   * Creates a resolved conditional block.
   *
   * @param condition resolved condition expression tree
   * @param members resolved members in declaration order
   */
  public ResolvedIfBlock {
    condition = Objects.requireNonNull(condition, "condition");
    members = List.copyOf(Objects.requireNonNull(members, "members"));
  }
}
