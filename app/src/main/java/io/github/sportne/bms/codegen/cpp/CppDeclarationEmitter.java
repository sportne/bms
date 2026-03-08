package io.github.sportne.bms.codegen.cpp;

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
 * Emits C++ struct field declarations for resolved message members.
 *
 * <p>This helper owns declaration flattening rules for nested and conditional blocks.
 */
final class CppDeclarationEmitter {
  /** Prevents instantiation of this static utility class. */
  private CppDeclarationEmitter() {}

  /**
   * Appends field declarations for all members that materialize as data fields.
   *
   * @param builder destination header builder
   * @param messageType message type being rendered
   * @param generationContext reusable lookup maps
   */
  static void appendMemberDeclarations(
      StringBuilder builder,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext) {
    appendMemberDeclarationsRecursive(
        builder, messageType.members(), messageType.effectiveNamespace(), generationContext);
  }

  /**
   * Appends field declarations for one member list, flattening nested/conditional blocks.
   *
   * @param builder destination header builder
   * @param members members to inspect in declaration order
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   */
  private static void appendMemberDeclarationsRecursive(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      String currentNamespace,
      CppCodeGenerator.GenerationContext generationContext) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        appendMemberDeclarationsRecursive(
            builder, resolvedIfBlock.members(), currentNamespace, generationContext);
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        appendMemberDeclarationsRecursive(
            builder, resolvedNestedType.members(), currentNamespace, generationContext);
        continue;
      }
      String cppType = CppTypeRenderer.memberCppType(member, currentNamespace, generationContext);
      if (cppType == null) {
        continue;
      }
      builder.append("  ").append(cppType).append(' ').append(memberName(member)).append("{};\n");
    }
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
