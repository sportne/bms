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

/** Orchestrates Java decode method generation and delegates member families to collaborators. */
final class JavaDecodeEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaDecodeEmitter() {}

  /**
   * Appends all decode methods for one generated message class.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  static void appendDecodeMethods(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    appendDecodeFromBytesMethod(builder, messageType);
    appendDecodeFromBufferMethod(builder, messageType, generationContext);
  }

  /**
   * Appends the `decode(byte[])` convenience method.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   */
  private static void appendDecodeFromBytesMethod(
      StringBuilder builder, ResolvedMessageType messageType) {
    builder
        .append("  /**\n")
        .append("   * Decodes a message instance from a byte array.\n")
        .append("   *\n")
        .append("   * @param bytes encoded message bytes\n")
        .append("   * @return decoded ")
        .append(messageType.name())
        .append(" value\n")
        .append("   */\n")
        .append("  public static ")
        .append(messageType.name())
        .append(" decode(byte[] bytes) {\n")
        .append("    Objects.requireNonNull(bytes, \"bytes\");\n")
        .append("    return decode(ByteBuffer.wrap(bytes));\n")
        .append("  }\n\n");
  }

  /**
   * Appends the `decode(ByteBuffer)` method that emits member decode statements.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendDecodeFromBufferMethod(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    builder
        .append("  /**\n")
        .append("   * Decodes a message instance from a byte buffer.\n")
        .append("   *\n")
        .append("   * @param input buffer positioned at the start of this message\n")
        .append("   * @return decoded ")
        .append(messageType.name())
        .append(" value\n")
        .append("   */\n")
        .append("  public static ")
        .append(messageType.name())
        .append(" decode(ByteBuffer input) {\n")
        .append("    Objects.requireNonNull(input, \"input\");\n");
    if (JavaHelperEmitter.containsChecksumMember(messageType.members())) {
      builder.append("    int messageStartPosition = input.position();\n");
    }
    builder
        .append("    ")
        .append(messageType.name())
        .append(" value = new ")
        .append(messageType.name())
        .append("();\n");

    JavaDecodeContext context =
        new JavaDecodeContext(
            builder, messageType, generationContext, primitiveFieldsByName(messageType), "value.");
    appendDecodeMembers(context, messageType.members());

    builder.append("    return value;\n");
    builder.append("  }\n\n");
  }

  /**
   * Appends decode statements for one member list recursively.
   *
   * @param context shared decode emission context
   * @param members members to decode
   */
  static void appendDecodeMembers(JavaDecodeContext context, List<ResolvedMessageMember> members) {
    for (ResolvedMessageMember member : members) {
      appendDecodeMember(context, member);
    }
  }

  /**
   * Appends decode statements for one member.
   *
   * @param context shared decode emission context
   * @param member member to decode
   */
  private static void appendDecodeMember(JavaDecodeContext context, ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      JavaDecodeFieldEmitter.appendDecodeField(context, resolvedField);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      JavaDecodeScalarEmitter.appendDecodeBitField(context, resolvedBitField);
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      JavaDecodeScalarEmitter.appendDecodeFloat(
          context.builder(), context.ownerPrefix() + resolvedFloat.name(), resolvedFloat);
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      JavaDecodeScalarEmitter.appendDecodeScaledInt(
          context.builder(), context.ownerPrefix() + resolvedScaledInt.name(), resolvedScaledInt);
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      JavaDecodeCollectionEmitter.appendDecodeArray(
          context,
          context.ownerPrefix() + resolvedArray.name(),
          resolvedArray,
          resolvedArray.name());
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      JavaDecodeCollectionEmitter.appendDecodeVector(
          context,
          context.ownerPrefix() + resolvedVector.name(),
          resolvedVector,
          resolvedVector.name());
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      JavaDecodeCollectionEmitter.appendDecodeBlobArray(
          context.builder(), context.ownerPrefix() + resolvedBlobArray.name(), resolvedBlobArray);
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      JavaDecodeCollectionEmitter.appendDecodeBlobVector(
          context,
          context.ownerPrefix() + resolvedBlobVector.name(),
          resolvedBlobVector,
          resolvedBlobVector.name());
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      JavaDecodeCollectionEmitter.appendDecodeVarString(
          context,
          context.ownerPrefix() + resolvedVarString.name(),
          resolvedVarString,
          resolvedVarString.name());
      return;
    }
    if (member instanceof ResolvedPad resolvedPad) {
      JavaDecodeConditionalEmitter.appendDecodePad(context.builder(), resolvedPad);
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      JavaDecodeConditionalEmitter.appendDecodeChecksum(context.builder(), resolvedChecksum);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      JavaDecodeConditionalEmitter.appendDecodeIfBlock(context, resolvedIfBlock);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendDecodeMembers(context, resolvedNestedType.members());
      return;
    }

    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }
}
