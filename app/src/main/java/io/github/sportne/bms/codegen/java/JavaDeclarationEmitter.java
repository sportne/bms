package io.github.sportne.bms.codegen.java;

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

/**
 * Emits Java field declarations for resolved message members.
 *
 * <p>This helper owns declaration flattening rules for nested and conditional blocks.
 */
final class JavaDeclarationEmitter {
  /** Prevents instantiation of this static utility class. */
  private JavaDeclarationEmitter() {}

  /**
   * Appends field declarations for message members.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  static void appendMemberDeclarations(
      StringBuilder builder,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext) {
    appendMemberDeclarationsForMembers(builder, messageType.members(), generationContext);
    builder.append("\n");
  }

  /**
   * Appends field declarations for one member list recursively.
   *
   * @param builder destination source builder
   * @param members members to render
   * @param generationContext reusable lookup maps
   */
  private static void appendMemberDeclarationsForMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      JavaCodeGenerator.GenerationContext generationContext) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        appendMemberDeclarationsForMembers(builder, resolvedIfBlock.members(), generationContext);
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        appendMemberDeclarationsForMembers(
            builder, resolvedNestedType.members(), generationContext);
        continue;
      }
      if (!isDeclarableMember(member)) {
        continue;
      }
      builder
          .append("  public ")
          .append(JavaTypeRenderer.javaTypeForMember(member, generationContext))
          .append(' ')
          .append(memberName(member))
          .append(";\n");
    }
  }

  /**
   * Returns whether one member kind produces a generated Java field declaration.
   *
   * @param member member to inspect
   * @return {@code true} when the member is emitted as a Java field
   */
  private static boolean isDeclarableMember(ResolvedMessageMember member) {
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
   * Resolves one member name.
   *
   * @param member member to inspect
   * @return member name
   */
  private static String memberName(ResolvedMessageMember member) {
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
