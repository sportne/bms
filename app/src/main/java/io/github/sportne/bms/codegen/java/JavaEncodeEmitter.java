package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.primitiveFieldsByName;

import io.github.sportne.bms.codegen.java.JavaCodeGenerator.GenerationContext;
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

/** Orchestrates Java encode method generation and delegates member families to collaborators. */
final class JavaEncodeEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaEncodeEmitter() {}

  /**
   * Appends encode method implementation.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  static void appendEncodeMethod(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    JavaEncodeContext context =
        new JavaEncodeContext(
            builder, messageType, generationContext, primitiveFieldsByName(messageType), "this.");

    builder.append("  public byte[] encode() {\n");
    builder.append("    ByteArrayOutputStream out = new ByteArrayOutputStream();\n");
    appendEncodeMembers(context, messageType.members());
    builder.append("    return out.toByteArray();\n");
    builder.append("  }\n\n");
  }

  /**
   * Appends encode statements for one member list recursively.
   *
   * @param context shared encode emission context
   * @param members members to encode
   */
  static void appendEncodeMembers(JavaEncodeContext context, List<ResolvedMessageMember> members) {
    for (ResolvedMessageMember member : members) {
      appendEncodeMember(context, member);
    }
  }

  /**
   * Appends encode statements for one member.
   *
   * @param context shared encode emission context
   * @param member member to encode
   */
  private static void appendEncodeMember(JavaEncodeContext context, ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      JavaEncodeFieldEmitter.appendEncodeField(context, resolvedField);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      JavaEncodeScalarEmitter.appendEncodeBitField(context, resolvedBitField);
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      JavaEncodeScalarEmitter.appendEncodeFloat(
          context.builder(),
          context.ownerPrefix() + resolvedFloat.name(),
          resolvedFloat,
          resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      JavaEncodeScalarEmitter.appendEncodeScaledInt(
          context.builder(),
          context.ownerPrefix() + resolvedScaledInt.name(),
          resolvedScaledInt,
          resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      JavaEncodeCollectionEmitter.appendEncodeArray(
          context,
          context.ownerPrefix() + resolvedArray.name(),
          resolvedArray,
          resolvedArray.name());
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      JavaEncodeCollectionEmitter.appendEncodeVector(
          context,
          context.ownerPrefix() + resolvedVector.name(),
          resolvedVector,
          resolvedVector.name());
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      JavaEncodeCollectionEmitter.appendEncodeBlobArray(
          context.builder(),
          context.ownerPrefix() + resolvedBlobArray.name(),
          resolvedBlobArray,
          resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      JavaEncodeCollectionEmitter.appendEncodeBlobVector(
          context,
          context.ownerPrefix() + resolvedBlobVector.name(),
          resolvedBlobVector,
          resolvedBlobVector.name());
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      JavaEncodeCollectionEmitter.appendEncodeVarString(
          context,
          context.ownerPrefix() + resolvedVarString.name(),
          resolvedVarString,
          resolvedVarString.name());
      return;
    }
    if (member instanceof ResolvedPad resolvedPad) {
      JavaEncodeConditionalEmitter.appendEncodePad(context.builder(), resolvedPad);
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      JavaEncodeConditionalEmitter.appendEncodeChecksum(context.builder(), resolvedChecksum);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      JavaEncodeConditionalEmitter.appendEncodeIfBlock(context, resolvedIfBlock);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendEncodeMembers(context, resolvedNestedType.members());
      return;
    }

    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }
}
