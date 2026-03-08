package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.primitiveFieldsByName;

import io.github.sportne.bms.codegen.cpp.CppCodeGenerator.GenerationContext;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedPad;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import java.util.List;

/** Orchestrates C++ encode method generation and delegates member families to collaborators. */
final class CppEncodeEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppEncodeEmitter() {}

  /**
   * Appends the public encode method for one generated C++ message struct.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  static void appendEncodeMethod(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    CppEncodeContext context =
        new CppEncodeContext(
            builder, messageType, generationContext, primitiveFieldsByName(messageType), "this->");
    builder
        .append("std::vector<std::uint8_t> ")
        .append(messageType.name())
        .append("::encode() const {\n")
        .append("  std::vector<std::uint8_t> out;\n");
    appendEncodeMembers(context, messageType.members());
    builder.append("  return out;\n").append("}\n\n");
  }

  /**
   * Appends encode statements for a member list.
   *
   * @param context shared encode emission context
   * @param members members to encode in declaration order
   */
  static void appendEncodeMembers(CppEncodeContext context, List<ResolvedMessageMember> members) {
    for (ResolvedMessageMember member : members) {
      appendEncodeMember(context, member);
    }
  }

  /**
   * Appends encode statements for one member.
   *
   * @param context shared encode emission context
   * @param member member being encoded
   */
  private static void appendEncodeMember(CppEncodeContext context, ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      CppEncodeFieldEmitter.appendEncodeField(context, resolvedField);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      CppEncodeFieldEmitter.appendEncodeBitField(context, resolvedBitField);
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      CppEncodeScalarEmitter.appendEncodeFloat(
          context.builder(),
          context.ownerPrefix() + resolvedFloat.name(),
          resolvedFloat,
          resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      CppEncodeScalarEmitter.appendEncodeScaledInt(
          context.builder(),
          context.ownerPrefix() + resolvedScaledInt.name(),
          resolvedScaledInt,
          resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      CppEncodeCollectionEmitter.appendEncodeArray(
          context, context.ownerPrefix() + resolvedArray.name(), resolvedArray);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      CppEncodeCollectionEmitter.appendEncodeVector(
          context,
          context.ownerPrefix() + resolvedVector.name(),
          resolvedVector,
          resolvedVector.name());
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      CppEncodeCollectionEmitter.appendEncodeBlobArray(
          context.builder(), context.ownerPrefix() + resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      CppEncodeCollectionEmitter.appendEncodeBlobVector(
          context,
          context.ownerPrefix() + resolvedBlobVector.name(),
          resolvedBlobVector,
          resolvedBlobVector.name());
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      CppEncodeCollectionEmitter.appendEncodeVarString(
          context,
          context.ownerPrefix() + resolvedVarString.name(),
          resolvedVarString,
          resolvedVarString.name());
      return;
    }
    if (member instanceof ResolvedPad resolvedPad) {
      CppEncodeConditionalEmitter.appendEncodePad(context.builder(), resolvedPad);
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      CppEncodeConditionalEmitter.appendEncodeChecksum(context.builder(), resolvedChecksum);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      CppEncodeConditionalEmitter.appendEncodeIfBlock(context, resolvedIfBlock);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendEncodeMembers(context, resolvedNestedType.members());
    }
  }
}
