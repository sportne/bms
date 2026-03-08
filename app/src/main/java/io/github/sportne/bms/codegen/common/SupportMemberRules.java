package io.github.sportne.bms.codegen.common;

import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared member-shape rules used by Java and C++ support checkers.
 *
 * <p>These helpers define which member kinds flatten into generated fields and how name-collision
 * checks should walk nested conditional and nested type blocks.
 */
public final class SupportMemberRules {
  /** Creates a utility-only helper class. */
  private SupportMemberRules() {}

  /**
   * Walks one member tree and reports duplicate flattened member names.
   *
   * <p>The collision callback receives labels like {@code message member name collision for value}
   * so backend-specific support checkers can map the label to their own diagnostics.
   *
   * @param members members to inspect
   * @param flattenedMemberNames mutable set that tracks names seen so far
   * @param ownerContext short owner label used in collision text
   * @param collisionLabelConsumer callback that receives one collision label per duplicate
   */
  public static void collectFlattenedMemberNameCollisions(
      List<ResolvedMessageMember> members,
      Set<String> flattenedMemberNames,
      String ownerContext,
      Consumer<String> collisionLabelConsumer) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        collectFlattenedMemberNameCollisions(
            resolvedIfBlock.members(), flattenedMemberNames, "if block", collisionLabelConsumer);
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        collectFlattenedMemberNameCollisions(
            resolvedNestedType.members(),
            flattenedMemberNames,
            "nested type " + resolvedNestedType.name(),
            collisionLabelConsumer);
        continue;
      }
      if (!isDeclarableMember(member)) {
        continue;
      }
      String flattenedName = memberName(member);
      if (!flattenedMemberNames.add(flattenedName)) {
        collisionLabelConsumer.accept(ownerContext + " member name collision for " + flattenedName);
      }
    }
  }

  /**
   * Returns whether one member kind produces a generated field declaration.
   *
   * @param member member to inspect
   * @return {@code true} when the member becomes a generated field
   */
  public static boolean isDeclarableMember(ResolvedMessageMember member) {
    return member instanceof ResolvedField
        || member instanceof ResolvedBitField
        || member instanceof ResolvedFloat
        || member instanceof ResolvedScaledInt
        || member instanceof ResolvedArray
        || member instanceof ResolvedVector
        || member instanceof ResolvedBlobArray
        || member instanceof ResolvedBlobVector
        || member instanceof ResolvedVarString;
  }

  /**
   * Resolves one declarable member name.
   *
   * @param member member to inspect
   * @return member name
   */
  public static String memberName(ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      return resolvedField.name();
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      return resolvedBitField.name();
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      return resolvedFloat.name();
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      return resolvedScaledInt.name();
    }
    if (member instanceof ResolvedArray resolvedArray) {
      return resolvedArray.name();
    }
    if (member instanceof ResolvedVector resolvedVector) {
      return resolvedVector.name();
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      return resolvedBlobArray.name();
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      return resolvedBlobVector.name();
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      return resolvedVarString.name();
    }
    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }
}
