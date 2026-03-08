package io.github.sportne.bms.codegen.common;

import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Shared traversal helpers for nested message members.
 *
 * <p>Members inside {@code if} blocks and nested {@code type} blocks are walked recursively in
 * deterministic declaration order.
 */
public final class MemberTraversal {
  /** Creates a utility-only helper class. */
  private MemberTraversal() {}

  /**
   * Visits members in depth-first declaration order.
   *
   * <p>The visitor receives each node before recursive children.
   *
   * @param members members to traverse
   * @param visitor callback called for each visited member
   */
  public static void visitDepthFirst(
      List<ResolvedMessageMember> members, Consumer<ResolvedMessageMember> visitor) {
    for (ResolvedMessageMember member : members) {
      visitor.accept(member);
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        visitDepthFirst(resolvedIfBlock.members(), visitor);
      } else if (member instanceof ResolvedMessageType resolvedMessageType) {
        visitDepthFirst(resolvedMessageType.members(), visitor);
      }
    }
  }

  /**
   * Returns whether any member in the recursive tree matches one predicate.
   *
   * @param members member tree to inspect
   * @param predicate predicate evaluated for each member
   * @return {@code true} when at least one member matches
   */
  public static boolean anyMatch(
      List<ResolvedMessageMember> members, Predicate<ResolvedMessageMember> predicate) {
    for (ResolvedMessageMember member : members) {
      if (predicate.test(member)) {
        return true;
      }
      if (member instanceof ResolvedIfBlock resolvedIfBlock
          && anyMatch(resolvedIfBlock.members(), predicate)) {
        return true;
      }
      if (member instanceof ResolvedMessageType resolvedMessageType
          && anyMatch(resolvedMessageType.members(), predicate)) {
        return true;
      }
    }
    return false;
  }
}
