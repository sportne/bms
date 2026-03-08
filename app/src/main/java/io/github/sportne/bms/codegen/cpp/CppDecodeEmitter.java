package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.containsChecksumMember;
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

/** Orchestrates C++ decode method generation and delegates member families to collaborators. */
final class CppDecodeEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppDecodeEmitter() {}

  /**
   * Appends all decode methods for one generated C++ message struct.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  static void appendDecodeMethods(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    appendDecodeMethod(builder, messageType);
    appendDecodeFromMethod(builder, messageType, generationContext);
  }

  /**
   * Appends the public decode method that validates full input consumption.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   */
  private static void appendDecodeMethod(StringBuilder builder, ResolvedMessageType messageType) {
    builder
        .append(messageType.name())
        .append(' ')
        .append(messageType.name())
        .append("::decode(std::span<const std::uint8_t> data) {\n")
        .append("  std::size_t cursor = 0;\n")
        .append("  ")
        .append(messageType.name())
        .append(" value = ")
        .append(messageType.name())
        .append("::decodeFrom(data, cursor);\n")
        .append("  if (cursor != data.size()) {\n")
        .append("    throw std::invalid_argument(\"Extra bytes remain after decoding ")
        .append(messageType.name())
        .append(".\");\n")
        .append("  }\n")
        .append("  return value;\n")
        .append("}\n\n");
  }

  /**
   * Appends the cursor-based decode method used by nested message decoding.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendDecodeFromMethod(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    builder
        .append(messageType.name())
        .append(' ')
        .append(messageType.name())
        .append("::decodeFrom(std::span<const std::uint8_t> data, std::size_t& cursor) {\n")
        .append("  ")
        .append(messageType.name())
        .append(" value{};\n");
    if (containsChecksumMember(messageType.members())) {
      builder.append("  std::size_t messageStartCursor = cursor;\n");
    }
    CppDecodeContext context =
        new CppDecodeContext(
            builder, messageType, generationContext, primitiveFieldsByName(messageType), "value.");
    appendDecodeMembers(context, messageType.members());
    builder.append("  return value;\n").append("}\n\n");
  }

  /**
   * Appends decode statements for a member list.
   *
   * @param context shared decode emission context
   * @param members members to decode in declaration order
   */
  static void appendDecodeMembers(CppDecodeContext context, List<ResolvedMessageMember> members) {
    for (ResolvedMessageMember member : members) {
      appendDecodeMember(context, member);
    }
  }

  /**
   * Appends decode statements for one member.
   *
   * @param context shared decode emission context
   * @param member member being decoded
   */
  private static void appendDecodeMember(CppDecodeContext context, ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      CppDecodeFieldEmitter.appendDecodeField(context, resolvedField);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      CppDecodeScalarEmitter.appendDecodeBitField(context, resolvedBitField);
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      CppDecodeScalarEmitter.appendDecodeFloat(
          context.builder(),
          context.ownerPrefix() + resolvedFloat.name(),
          resolvedFloat,
          resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      CppDecodeScalarEmitter.appendDecodeScaledInt(
          context.builder(),
          context.ownerPrefix() + resolvedScaledInt.name(),
          resolvedScaledInt,
          resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      CppDecodeCollectionEmitter.appendDecodeArray(
          context, context.ownerPrefix() + resolvedArray.name(), resolvedArray);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      CppDecodeCollectionEmitter.appendDecodeVector(
          context,
          context.ownerPrefix() + resolvedVector.name(),
          resolvedVector,
          resolvedVector.name());
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      CppDecodeCollectionEmitter.appendDecodeBlobArray(
          context.builder(),
          context.ownerPrefix() + resolvedBlobArray.name(),
          resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      CppDecodeCollectionEmitter.appendDecodeBlobVector(
          context,
          context.ownerPrefix() + resolvedBlobVector.name(),
          resolvedBlobVector,
          resolvedBlobVector.name());
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      CppDecodeCollectionEmitter.appendDecodeVarString(
          context,
          context.ownerPrefix() + resolvedVarString.name(),
          resolvedVarString,
          resolvedVarString.name());
      return;
    }
    if (member instanceof ResolvedPad resolvedPad) {
      CppDecodeConditionalEmitter.appendDecodePad(context.builder(), resolvedPad);
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      CppDecodeConditionalEmitter.appendDecodeChecksum(context.builder(), resolvedChecksum);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      CppDecodeConditionalEmitter.appendDecodeIfBlock(context, resolvedIfBlock);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendDecodeMembers(context, resolvedNestedType.members());
    }
  }
}
