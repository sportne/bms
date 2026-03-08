package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.*;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.codegen.cpp.CppCodeGenerator.GenerationContext;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedPad;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** Emits C++ decode method bodies for generated message structs. */
final class CppDecodeEmitter {
  /** Prevents instantiation of this static utility class. */
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
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
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
    appendDecodeMembers(
        builder,
        messageType.members(),
        messageType,
        generationContext,
        primitiveFieldByName,
        "value.");
    builder.append("  return value;\n").append("}\n\n");
  }

  /**
   * Appends decode statements for a member list.
   *
   * @param builder destination source builder
   * @param members members to decode in declaration order
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendDecodeMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    for (ResolvedMessageMember member : members) {
      appendDecodeMember(
          builder, member, messageType, generationContext, primitiveFieldByName, ownerPrefix);
    }
  }

  /**
   * Appends decode statements for one member.
   *
   * @param builder destination source builder
   * @param member member being decoded
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendDecodeMember(
      StringBuilder builder,
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (member instanceof ResolvedField resolvedField) {
      appendDecodeField(
          builder,
          resolvedField.typeRef(),
          ownerPrefix + resolvedField.name(),
          resolvedField.name(),
          resolvedField.endian(),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      appendDecodePrimitive(
          builder,
          ownerPrefix + resolvedBitField.name(),
          bitFieldStoragePrimitive(resolvedBitField.size()),
          resolvedBitField.endian(),
          resolvedBitField.name());
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      appendDecodeFloat(
          builder, ownerPrefix + resolvedFloat.name(), resolvedFloat, resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      appendDecodeScaledInt(
          builder,
          ownerPrefix + resolvedScaledInt.name(),
          resolvedScaledInt,
          resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      appendDecodeArray(
          builder,
          ownerPrefix + resolvedArray.name(),
          resolvedArray,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      appendDecodeVector(
          builder,
          ownerPrefix + resolvedVector.name(),
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          resolvedVector.name());
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      appendDecodeBlobArray(
          builder, ownerPrefix + resolvedBlobArray.name(), resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      appendDecodeBlobVector(
          builder,
          ownerPrefix + resolvedBlobVector.name(),
          resolvedBlobVector,
          primitiveFieldByName,
          ownerPrefix,
          resolvedBlobVector.name());
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      appendDecodeVarString(
          builder,
          ownerPrefix + resolvedVarString.name(),
          resolvedVarString,
          primitiveFieldByName,
          ownerPrefix,
          resolvedVarString.name());
      return;
    }
    if (member instanceof ResolvedPad resolvedPad) {
      appendDecodePad(builder, resolvedPad);
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      appendDecodeChecksum(builder, resolvedChecksum);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      appendDecodeIfBlock(
          builder,
          resolvedIfBlock,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendDecodeMembers(
          builder,
          resolvedNestedType.members(),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
  }

  /**
   * Appends decode statements for one field (including reusable type references).
   *
   * @param builder destination source builder
   * @param typeRef field type reference
   * @param targetExpression assignment target expression
   * @param fieldName field name used in helper labels
   * @param endian optional endian override
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendDecodeField(
      StringBuilder builder,
      ResolvedTypeRef typeRef,
      String targetExpression,
      String fieldName,
      Endian endian,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendDecodePrimitive(
          builder, targetExpression, primitiveTypeRef.primitiveType(), endian, fieldName);
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      builder
          .append("  ")
          .append(targetExpression)
          .append(" = ")
          .append(
              cppMessageTypeName(
                  messageTypeRef.messageTypeName(),
                  messageType.effectiveNamespace(),
                  generationContext))
          .append("::decodeFrom(data, cursor);\n");
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      appendDecodeFloat(
          builder,
          targetExpression,
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName()),
          fieldName);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      appendDecodeScaledInt(
          builder,
          targetExpression,
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName()),
          fieldName);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      appendDecodeArray(
          builder,
          targetExpression,
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName()),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      appendDecodeVector(
          builder,
          targetExpression,
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName()),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef) {
      appendDecodeBlobArray(builder, targetExpression, fieldName);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      appendDecodeBlobVector(
          builder,
          targetExpression,
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName()),
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      appendDecodeVarString(
          builder,
          targetExpression,
          generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName()),
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    throw new IllegalStateException(
        "Unsupported field type reference in C++ decode: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Appends primitive decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param primitiveType primitive wire type
   * @param endian optional endian override
   * @param label label used in runtime exception text
   */
  private static void appendDecodePrimitive(
      StringBuilder builder,
      String targetExpression,
      PrimitiveType primitiveType,
      Endian endian,
      String label) {
    boolean littleEndian = endian == Endian.LITTLE;
    builder
        .append("  ")
        .append(targetExpression)
        .append(" = readIntegral<")
        .append(primitiveType.cppTypeName())
        .append(">(data, cursor, ")
        .append(littleEndian ? "true" : "false")
        .append(", \"")
        .append(label)
        .append("\");\n");
  }

  /**
   * Appends float decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedFloat float definition
   * @param fieldName field/member name used in exception text
   */
  private static void appendDecodeFloat(
      StringBuilder builder,
      String targetExpression,
      ResolvedFloat resolvedFloat,
      String fieldName) {
    String littleEndianLiteral = resolvedFloat.endian() == Endian.LITTLE ? "true" : "false";
    String scaleLiteral = decimalLiteral(resolvedFloat.scale());
    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      if (resolvedFloat.size() == FloatSize.F16) {
        builder
            .append("  ")
            .append(targetExpression)
            .append(" = static_cast<double>(readFloat16(data, cursor, ")
            .append(littleEndianLiteral)
            .append(", \"")
            .append(fieldName)
            .append("\"));\n");
        return;
      }
      if (resolvedFloat.size() == FloatSize.F32) {
        builder
            .append("  ")
            .append(targetExpression)
            .append(" = static_cast<double>(readFloat32(data, cursor, ")
            .append(littleEndianLiteral)
            .append(", \"")
            .append(fieldName)
            .append("\"));\n");
        return;
      }
      builder
          .append("  ")
          .append(targetExpression)
          .append(" = readFloat64(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\");\n");
      return;
    }

    if (resolvedFloat.size() == FloatSize.F16) {
      builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int16_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      return;
    }
    if (resolvedFloat.size() == FloatSize.F32) {
      builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int32_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      return;
    }
    builder
        .append("  ")
        .append(targetExpression)
        .append(" = static_cast<double>(readIntegral<std::int64_t>(data, cursor, ")
        .append(littleEndianLiteral)
        .append(", \"")
        .append(fieldName)
        .append("\")) * ")
        .append(scaleLiteral)
        .append(";\n");
  }

  /**
   * Appends scaled-int decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedScaledInt scaled-int definition
   * @param fieldName field/member name used in exception text
   */
  private static void appendDecodeScaledInt(
      StringBuilder builder,
      String targetExpression,
      ResolvedScaledInt resolvedScaledInt,
      String fieldName) {
    String littleEndianLiteral = resolvedScaledInt.endian() == Endian.LITTLE ? "true" : "false";
    String scaleLiteral = decimalLiteral(resolvedScaledInt.scale());
    switch (resolvedScaledInt.baseType()) {
      case INT8 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int8_t>(data, cursor, false, \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT8 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::uint8_t>(data, cursor, false, \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT16 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int16_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT16 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::uint16_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT32 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int32_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT32 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::uint32_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT64 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::int64_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT64 -> builder
          .append("  ")
          .append(targetExpression)
          .append(" = static_cast<double>(readIntegral<std::uint64_t>(data, cursor, ")
          .append(littleEndianLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\")) * ")
          .append(scaleLiteral)
          .append(";\n");
    }
  }

  /**
   * Appends fixed-array decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedArray array definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendDecodeArray(
      StringBuilder builder,
      String targetExpression,
      ResolvedArray resolvedArray,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String loopIndex = toLoopIndexName(resolvedArray.name());
    String itemName = toLoopItemName(resolvedArray.name());
    builder
        .append("  for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndex)
        .append("++) {\n");
    appendDecodeCollectionElement(
        builder,
        itemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix,
        resolvedArray.name());
    builder
        .append("    ")
        .append(targetExpression)
        .append('[')
        .append(loopIndex)
        .append("] = ")
        .append(itemName)
        .append(";\n")
        .append("  }\n");
  }

  /**
   * Appends vector decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (resolvedVector.lengthMode() instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      appendDecodeCountedVector(
          builder,
          targetExpression,
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          ownerName,
          resolvedCountFieldLength);
      return;
    }
    appendDecodeTerminatedVector(
        builder, targetExpression, resolvedVector, messageType, generationContext, ownerName);
  }

  /**
   * Appends decode statements for count-based vectors.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   * @param resolvedCountFieldLength count-field length mode
   */
  private static void appendDecodeCountedVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName,
      ResolvedCountFieldLength resolvedCountFieldLength) {
    PrimitiveType countType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    if (countType == null) {
      return;
    }
    String itemName = toLoopItemName(ownerName);
    String loopIndex = toLoopIndexName(ownerName);
    builder
        .append("  {\n")
        .append("    std::size_t expected")
        .append(toPascalCase(ownerName))
        .append("Count = requireCount(")
        .append(ownerPrefix)
        .append(resolvedCountFieldLength.ref())
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("    ")
        .append(targetExpression)
        .append(".reserve(expected")
        .append(toPascalCase(ownerName))
        .append("Count);\n")
        .append("    for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < expected")
        .append(toPascalCase(ownerName))
        .append("Count; ")
        .append(loopIndex)
        .append("++) {\n");
    appendDecodeCollectionElement(
        builder,
        itemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix,
        ownerName);
    builder
        .append("      ")
        .append(targetExpression)
        .append(".push_back(")
        .append(itemName)
        .append(");\n")
        .append("    }\n")
        .append("  }\n");
  }

  /**
   * Appends decode statements for terminator-based vectors.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeTerminatedVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      String ownerName) {
    String literal = terminatorLiteral(resolvedVector.lengthMode());
    BigInteger numericLiteral = parseNumericLiteral(literal);
    PrimitiveType primitiveType =
        ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
    String itemName = toLoopItemName(ownerName);

    builder
        .append("  ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("  while (true) {\n");
    appendDecodeCollectionElement(
        builder,
        itemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        messageType,
        generationContext,
        Map.of(),
        "value.",
        ownerName);
    builder
        .append("    if (")
        .append(itemName)
        .append(" == ")
        .append(primitiveLiteralExpression(primitiveType, numericLiteral))
        .append(") {\n")
        .append("      break;\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(".push_back(")
        .append(itemName)
        .append(");\n")
        .append("  }\n");
  }

  /**
   * Appends decode statements for one collection element.
   *
   * @param builder destination source builder
   * @param localName local variable that receives decoded value
   * @param elementTypeRef collection element type
   * @param endian optional endian override
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeCollectionElement(
      StringBuilder builder,
      String localName,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      builder
          .append("    ")
          .append(primitiveTypeRef.primitiveType().cppTypeName())
          .append(' ')
          .append(localName)
          .append(" = readIntegral<")
          .append(primitiveTypeRef.primitiveType().cppTypeName())
          .append(">(data, cursor, ")
          .append(endian == Endian.LITTLE ? "true" : "false")
          .append(", \"")
          .append(ownerName)
          .append("_item\");\n");
      return;
    }
    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String cppType =
          cppMessageTypeName(
              messageTypeRef.messageTypeName(),
              messageType.effectiveNamespace(),
              generationContext);
      builder
          .append("    ")
          .append(cppType)
          .append(' ')
          .append(localName)
          .append(" = ")
          .append(cppType)
          .append("::decodeFrom(data, cursor);\n");
      return;
    }
    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      builder.append("    double ").append(localName).append("{};\n");
      appendDecodeFloat(builder, localName, resolvedFloat, ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      builder.append("    double ").append(localName).append("{};\n");
      appendDecodeScaledInt(builder, localName, resolvedScaledInt, ownerName + "_item");
      return;
    }
    throw new IllegalStateException(
        "Unsupported collection element type in C++ decode: "
            + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Appends blob-array decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param fieldName field name used in helper labels
   */
  private static void appendDecodeBlobArray(
      StringBuilder builder, String targetExpression, String fieldName) {
    builder
        .append("  requireReadable(data, cursor, ")
        .append(targetExpression)
        .append(".size(), \"")
        .append(fieldName)
        .append("\");\n")
        .append("  std::copy_n(data.begin() + static_cast<std::ptrdiff_t>(cursor), ")
        .append(targetExpression)
        .append(".size(), ")
        .append(targetExpression)
        .append(".begin());\n")
        .append("  cursor += ")
        .append(targetExpression)
        .append(".size();\n");
  }

  /**
   * Appends blob-vector decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedBlobVector blob-vector definition
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeBlobVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedBlobVector resolvedBlobVector,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (resolvedBlobVector.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      PrimitiveType countType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
      if (countType == null) {
        return;
      }
      builder
          .append("  {\n")
          .append("    std::size_t expected")
          .append(toPascalCase(ownerName))
          .append("Count = requireCount(")
          .append(ownerPrefix)
          .append(resolvedCountFieldLength.ref())
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    requireReadable(data, cursor, expected")
          .append(toPascalCase(ownerName))
          .append("Count, \"")
          .append(ownerName)
          .append("\");\n")
          .append("    ")
          .append(targetExpression)
          .append(".assign(\n")
          .append("        data.begin() + static_cast<std::ptrdiff_t>(cursor),\n")
          .append("        data.begin() + static_cast<std::ptrdiff_t>(cursor + expected")
          .append(toPascalCase(ownerName))
          .append("Count));\n")
          .append("    cursor += expected")
          .append(toPascalCase(ownerName))
          .append("Count;\n")
          .append("  }\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedBlobVector.lengthMode()));
    builder
        .append("  ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("  while (true) {\n")
        .append("    std::uint8_t nextByte = readIntegral<std::uint8_t>(data, cursor, false, \"")
        .append(ownerName)
        .append("_item\");\n")
        .append("    if (nextByte == ")
        .append(primitiveLiteralExpression(PrimitiveType.UINT8, numericLiteral))
        .append(") {\n")
        .append("      break;\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(".push_back(nextByte);\n")
        .append("  }\n");
  }

  /**
   * Appends varString decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVarString varString definition
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param fieldName field/member name used in helper labels
   */
  private static void appendDecodeVarString(
      StringBuilder builder,
      String targetExpression,
      ResolvedVarString resolvedVarString,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String fieldName) {
    if (resolvedVarString.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      PrimitiveType countType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
      if (countType == null) {
        return;
      }
      builder
          .append("  std::size_t expected")
          .append(toPascalCase(fieldName))
          .append("Length = requireCount(")
          .append(ownerPrefix)
          .append(resolvedCountFieldLength.ref())
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("  requireReadable(data, cursor, expected")
          .append(toPascalCase(fieldName))
          .append("Length, \"")
          .append(fieldName)
          .append("\");\n")
          .append("  ")
          .append(targetExpression)
          .append(".assign(\n")
          .append("      reinterpret_cast<const char*>(data.data() + cursor),\n")
          .append("      expected")
          .append(toPascalCase(fieldName))
          .append("Length);\n")
          .append("  cursor += expected")
          .append(toPascalCase(fieldName))
          .append("Length;\n");
      return;
    }
    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedVarString.lengthMode()));
    builder
        .append("  ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("  while (true) {\n")
        .append("    std::uint8_t nextByte = readIntegral<std::uint8_t>(data, cursor, false, \"")
        .append(fieldName)
        .append("_item\");\n")
        .append("    if (nextByte == ")
        .append(primitiveLiteralExpression(PrimitiveType.UINT8, numericLiteral))
        .append(") {\n")
        .append("      break;\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(".push_back(static_cast<char>(nextByte));\n")
        .append("  }\n");
  }

  /**
   * Appends pad decode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  private static void appendDecodePad(StringBuilder builder, ResolvedPad resolvedPad) {
    builder
        .append("  requireReadable(data, cursor, ")
        .append(resolvedPad.bytes())
        .append("U, \"pad\");\n")
        .append("  cursor += ")
        .append(resolvedPad.bytes())
        .append("U;\n");
  }

  /**
   * Appends checksum decode statements.
   *
   * @param builder destination source builder
   * @param resolvedChecksum checksum definition
   */
  private static void appendDecodeChecksum(
      StringBuilder builder, ResolvedChecksum resolvedChecksum) {
    ChecksumRangeRules.ChecksumRange checksumRange =
        requiredChecksumRange(resolvedChecksum.range());
    String algorithm = resolvedChecksum.algorithm();
    builder
        .append("  {\n")
        .append("    std::span<const std::uint8_t> messageBytes(\n")
        .append("        data.data() + messageStartCursor,\n")
        .append("        data.size() - messageStartCursor);\n")
        .append("    validateChecksumRange(messageBytes.size(), ")
        .append(checksumRange.startInclusive())
        .append(", ")
        .append(checksumRange.endInclusive())
        .append(", \"")
        .append(algorithm)
        .append("\", \"")
        .append(resolvedChecksum.range())
        .append("\");\n");
    if ("crc16".equals(algorithm)) {
      builder
          .append("    std::uint16_t expectedChecksum = readIntegral<std::uint16_t>(\n")
          .append("        data, cursor, false, \"")
          .append(algorithm)
          .append("_checksum\");\n")
          .append("    std::uint16_t actualChecksum = crc16(messageBytes, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    if (expectedChecksum != actualChecksum) {\n")
          .append("      throw std::invalid_argument(\"Checksum mismatch for ")
          .append(algorithm)
          .append(" range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("    }\n");
    } else if ("crc32".equals(algorithm)) {
      builder
          .append("    std::uint32_t expectedChecksum = readIntegral<std::uint32_t>(\n")
          .append("        data, cursor, false, \"")
          .append(algorithm)
          .append("_checksum\");\n")
          .append("    std::uint32_t actualChecksum = crc32(messageBytes, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    if (expectedChecksum != actualChecksum) {\n")
          .append("      throw std::invalid_argument(\"Checksum mismatch for ")
          .append(algorithm)
          .append(" range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("    }\n");
    } else if ("crc64".equals(algorithm)) {
      builder
          .append("    std::uint64_t expectedChecksum = readIntegral<std::uint64_t>(\n")
          .append("        data, cursor, false, \"")
          .append(algorithm)
          .append("_checksum\");\n")
          .append("    std::uint64_t actualChecksum = crc64(messageBytes, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    if (expectedChecksum != actualChecksum) {\n")
          .append("      throw std::invalid_argument(\"Checksum mismatch for ")
          .append(algorithm)
          .append(" range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("    }\n");
    } else if ("sha256".equals(algorithm)) {
      builder
          .append("    requireReadable(data, cursor, 32U, \"sha256_checksum\");\n")
          .append("    std::array<std::uint8_t, 32> expectedChecksum{};\n")
          .append("    std::copy_n(data.begin() + static_cast<std::ptrdiff_t>(cursor), 32, ")
          .append("expectedChecksum.begin());\n")
          .append("    cursor += 32U;\n")
          .append("    std::array<std::uint8_t, 32> actualChecksum = sha256(messageBytes, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    if (expectedChecksum != actualChecksum) {\n")
          .append("      throw std::invalid_argument(\"Checksum mismatch for ")
          .append(algorithm)
          .append(" range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("    }\n");
    } else {
      throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm);
    }
    builder.append("  }\n");
  }

  /**
   * Appends conditional-block decode statements.
   *
   * @param builder destination source builder
   * @param resolvedIfBlock conditional block definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for field access
   */
  private static void appendDecodeIfBlock(
      StringBuilder builder,
      ResolvedIfBlock resolvedIfBlock,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    builder
        .append("  if (")
        .append(ifConditionExpression(resolvedIfBlock.condition(), ownerPrefix))
        .append(") {\n");
    appendDecodeMembers(
        builder,
        resolvedIfBlock.members(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix);
    builder.append("  }\n");
  }
}
