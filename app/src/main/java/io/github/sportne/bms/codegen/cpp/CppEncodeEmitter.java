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
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
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

/** Emits C++ encode method bodies for generated message structs. */
final class CppEncodeEmitter {
  /** Prevents instantiation of this static utility class. */
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
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    builder
        .append("std::vector<std::uint8_t> ")
        .append(messageType.name())
        .append("::encode() const {\n")
        .append("  std::vector<std::uint8_t> out;\n");
    appendEncodeMembers(
        builder,
        messageType.members(),
        messageType,
        generationContext,
        primitiveFieldByName,
        "this->");
    builder.append("  return out;\n").append("}\n\n");
  }

  /**
   * Appends encode statements for a member list.
   *
   * @param builder destination source builder
   * @param members members to encode in declaration order
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendEncodeMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    for (ResolvedMessageMember member : members) {
      appendEncodeMember(
          builder, member, messageType, generationContext, primitiveFieldByName, ownerPrefix);
    }
  }

  /**
   * Appends encode statements for one member.
   *
   * @param builder destination source builder
   * @param member member being encoded
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendEncodeMember(
      StringBuilder builder,
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (member instanceof ResolvedField resolvedField) {
      appendEncodeField(
          builder,
          resolvedField.typeRef(),
          "this->" + resolvedField.name(),
          resolvedField.name(),
          resolvedField.endian(),
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      appendEncodePrimitive(
          builder,
          "this->" + resolvedBitField.name(),
          bitFieldStoragePrimitive(resolvedBitField.size()),
          resolvedBitField.endian(),
          resolvedBitField.name());
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      appendEncodeFloat(
          builder, "this->" + resolvedFloat.name(), resolvedFloat, resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      appendEncodeScaledInt(
          builder,
          "this->" + resolvedScaledInt.name(),
          resolvedScaledInt,
          resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      appendEncodeArray(
          builder,
          "this->" + resolvedArray.name(),
          resolvedArray,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      appendEncodeVector(
          builder,
          "this->" + resolvedVector.name(),
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          resolvedVector.name());
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      appendEncodeBlobArray(builder, "this->" + resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      appendEncodeBlobVector(
          builder,
          "this->" + resolvedBlobVector.name(),
          resolvedBlobVector,
          primitiveFieldByName,
          ownerPrefix,
          resolvedBlobVector.name());
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      appendEncodeVarString(
          builder,
          "this->" + resolvedVarString.name(),
          resolvedVarString,
          primitiveFieldByName,
          ownerPrefix,
          resolvedVarString.name());
      return;
    }
    if (member instanceof ResolvedPad resolvedPad) {
      appendEncodePad(builder, resolvedPad);
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      appendEncodeChecksum(builder, resolvedChecksum);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      appendEncodeIfBlock(
          builder,
          resolvedIfBlock,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendEncodeMembers(
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
   * Appends encode statements for one field (including reusable type references).
   *
   * @param builder destination source builder
   * @param typeRef field type reference
   * @param valueExpression expression that resolves to the field value
   * @param fieldName field name used in helper labels
   * @param endian optional endian override
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendEncodeField(
      StringBuilder builder,
      ResolvedTypeRef typeRef,
      String valueExpression,
      String fieldName,
      Endian endian,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendEncodePrimitive(
          builder, valueExpression, primitiveTypeRef.primitiveType(), endian, fieldName);
      return;
    }
    if (typeRef instanceof MessageTypeRef) {
      builder
          .append("  {\n")
          .append("    std::vector<std::uint8_t> nested = ")
          .append(valueExpression)
          .append(".encode();\n")
          .append("    out.insert(out.end(), nested.begin(), nested.end());\n")
          .append("  }\n");
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      appendEncodeFloat(builder, valueExpression, resolvedFloat, fieldName);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      appendEncodeScaledInt(builder, valueExpression, resolvedScaledInt, fieldName);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      appendEncodeArray(
          builder,
          valueExpression,
          resolvedArray,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      appendEncodeVector(
          builder,
          valueExpression,
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef) {
      appendEncodeBlobArray(builder, valueExpression);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      appendEncodeBlobVector(
          builder,
          valueExpression,
          resolvedBlobVector,
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName());
      appendEncodeVarString(
          builder,
          valueExpression,
          resolvedVarString,
          primitiveFieldByName,
          ownerPrefix,
          fieldName);
      return;
    }
    throw new IllegalStateException(
        "Unsupported field type reference in C++ encode: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Appends primitive encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the primitive value
   * @param primitiveType primitive wire type
   * @param endian optional endian override
   * @param label label used in runtime exception text
   */
  private static void appendEncodePrimitive(
      StringBuilder builder,
      String valueExpression,
      PrimitiveType primitiveType,
      Endian endian,
      String label) {
    boolean littleEndian = endian == Endian.LITTLE;
    String littleEndianLiteral = littleEndian ? "true" : "false";
    builder
        .append("  writeIntegral<")
        .append(primitiveType.cppTypeName())
        .append(">(out, ")
        .append(valueExpression)
        .append(", ")
        .append(littleEndianLiteral)
        .append(");\n");
    if (primitiveType == PrimitiveType.UINT8 || primitiveType == PrimitiveType.INT8) {
      return;
    }
    builder.append("  (void)\"").append(label).append("\";\n");
  }

  /**
   * Appends float encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the logical float value
   * @param resolvedFloat float definition
   * @param fieldName field/member name used in exception text
   */
  private static void appendEncodeFloat(
      StringBuilder builder,
      String valueExpression,
      ResolvedFloat resolvedFloat,
      String fieldName) {
    String littleEndianLiteral = resolvedFloat.endian() == Endian.LITTLE ? "true" : "false";
    String scaleLiteral = decimalLiteral(resolvedFloat.scale());

    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      if (resolvedFloat.size() == FloatSize.F16) {
        builder
            .append("  writeFloat16(out, static_cast<float>(")
            .append(valueExpression)
            .append("), ")
            .append(littleEndianLiteral)
            .append(");\n");
        return;
      }
      if (resolvedFloat.size() == FloatSize.F32) {
        builder
            .append("  writeFloat32(out, static_cast<float>(")
            .append(valueExpression)
            .append("), ")
            .append(littleEndianLiteral)
            .append(");\n");
        return;
      }
      builder
          .append("  writeFloat64(out, ")
          .append(valueExpression)
          .append(", ")
          .append(littleEndianLiteral)
          .append(");\n");
      return;
    }

    if (resolvedFloat.size() == FloatSize.F16) {
      builder
          .append("  writeIntegral<std::int16_t>(out, scaleToSignedRaw<std::int16_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      return;
    }
    if (resolvedFloat.size() == FloatSize.F32) {
      builder
          .append("  writeIntegral<std::int32_t>(out, scaleToSignedRaw<std::int32_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      return;
    }
    builder
        .append("  writeIntegral<std::int64_t>(out, scaleToSignedRaw<std::int64_t>(")
        .append(valueExpression)
        .append(", ")
        .append(scaleLiteral)
        .append(", \"")
        .append(fieldName)
        .append("\"), ")
        .append(littleEndianLiteral)
        .append(");\n");
  }

  /**
   * Appends scaled-int encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the logical scaled value
   * @param resolvedScaledInt scaled-int definition
   * @param fieldName field/member name used in exception text
   */
  private static void appendEncodeScaledInt(
      StringBuilder builder,
      String valueExpression,
      ResolvedScaledInt resolvedScaledInt,
      String fieldName) {
    String littleEndianLiteral = resolvedScaledInt.endian() == Endian.LITTLE ? "true" : "false";
    String scaleLiteral = decimalLiteral(resolvedScaledInt.scale());
    switch (resolvedScaledInt.baseType()) {
      case INT8 -> builder
          .append("  writeIntegral<std::int8_t>(out, scaleToSignedRaw<std::int8_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), false);\n");
      case UINT8 -> builder
          .append("  writeIntegral<std::uint8_t>(out, scaleToUnsignedRaw<std::uint8_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), false);\n");
      case INT16 -> builder
          .append("  writeIntegral<std::int16_t>(out, scaleToSignedRaw<std::int16_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case UINT16 -> builder
          .append("  writeIntegral<std::uint16_t>(out, scaleToUnsignedRaw<std::uint16_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case INT32 -> builder
          .append("  writeIntegral<std::int32_t>(out, scaleToSignedRaw<std::int32_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case UINT32 -> builder
          .append("  writeIntegral<std::uint32_t>(out, scaleToUnsignedRaw<std::uint32_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case INT64 -> builder
          .append("  writeIntegral<std::int64_t>(out, scaleToSignedRaw<std::int64_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
      case UINT64 -> builder
          .append("  writeIntegral<std::uint64_t>(out, scaleToUnsignedRaw<std::uint64_t>(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(littleEndianLiteral)
          .append(");\n");
    }
  }

  /**
   * Appends fixed-array encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the array value
   * @param resolvedArray array definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   */
  private static void appendEncodeArray(
      StringBuilder builder,
      String valueExpression,
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
        .append("++) {\n")
        .append("    auto ")
        .append(itemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndex)
        .append("];\n");
    appendEncodeCollectionElement(
        builder,
        itemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix,
        resolvedArray.name());
    builder.append("  }\n");
  }

  /**
   * Appends vector encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the vector value
   * @param resolvedVector vector definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendEncodeVector(
      StringBuilder builder,
      String valueExpression,
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    appendCountValidation(
        builder,
        resolvedVector.lengthMode(),
        valueExpression,
        primitiveFieldByName,
        ownerPrefix,
        ownerName);
    String loopIndex = toLoopIndexName(ownerName);
    String itemName = toLoopItemName(ownerName);
    builder
        .append("  for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < ")
        .append(valueExpression)
        .append(".size(); ")
        .append(loopIndex)
        .append("++) {\n")
        .append("    auto ")
        .append(itemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndex)
        .append("];\n");
    appendEncodeCollectionElement(
        builder,
        itemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix,
        ownerName);
    builder.append("  }\n");

    String terminatorLiteral = terminatorLiteral(resolvedVector.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      PrimitiveType primitiveType =
          ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
      appendEncodePrimitive(
          builder,
          primitiveLiteralExpression(primitiveType, numericLiteral),
          primitiveType,
          resolvedVector.endian(),
          ownerName + "_terminator");
    }
  }

  /**
   * Appends blob-array encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob-array value
   */
  private static void appendEncodeBlobArray(StringBuilder builder, String valueExpression) {
    builder
        .append("  out.insert(out.end(), ")
        .append(valueExpression)
        .append(".begin(), ")
        .append(valueExpression)
        .append(".end());\n");
  }

  /**
   * Appends blob-vector encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob-vector value
   * @param resolvedBlobVector blob-vector definition
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendEncodeBlobVector(
      StringBuilder builder,
      String valueExpression,
      ResolvedBlobVector resolvedBlobVector,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    appendCountValidation(
        builder,
        resolvedBlobVector.lengthMode(),
        valueExpression,
        primitiveFieldByName,
        ownerPrefix,
        ownerName);
    builder
        .append("  out.insert(out.end(), ")
        .append(valueExpression)
        .append(".begin(), ")
        .append(valueExpression)
        .append(".end());\n");
    String terminatorLiteral = terminatorLiteral(resolvedBlobVector.lengthMode());
    if (terminatorLiteral != null) {
      appendEncodePrimitive(
          builder,
          primitiveLiteralExpression(PrimitiveType.UINT8, parseNumericLiteral(terminatorLiteral)),
          PrimitiveType.UINT8,
          Endian.BIG,
          ownerName + "_terminator");
    }
  }

  /**
   * Appends encode statements for one collection element.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to one element value
   * @param elementTypeRef collection element type
   * @param endian optional endian override
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendEncodeCollectionElement(
      StringBuilder builder,
      String valueExpression,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendEncodePrimitive(
          builder, valueExpression, primitiveTypeRef.primitiveType(), endian, ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof MessageTypeRef) {
      builder
          .append("    {\n")
          .append("      std::vector<std::uint8_t> nested = ")
          .append(valueExpression)
          .append(".encode();\n")
          .append("      out.insert(out.end(), nested.begin(), nested.end());\n")
          .append("    }\n");
      return;
    }
    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      appendEncodeFloat(
          builder,
          valueExpression,
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName()),
          ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      appendEncodeScaledInt(
          builder,
          valueExpression,
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName()),
          ownerName + "_item");
      return;
    }
    throw new IllegalStateException(
        "Unsupported collection element type in C++ encode: "
            + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Appends count-field validation for vector/blob-vector encode paths.
   *
   * @param builder destination source builder
   * @param lengthMode vector/blob length mode
   * @param valueExpression expression that resolves to collection value
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param ownerName owner name used in helper labels
   */
  private static void appendCountValidation(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      String valueExpression,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String ownerName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }
    PrimitiveType countType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    if (countType == null) {
      return;
    }
    String countExpression = ownerPrefix + resolvedCountFieldLength.ref();
    builder
        .append("  std::size_t expected")
        .append(toPascalCase(ownerName))
        .append("Count = requireCount(")
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("  if (")
        .append(valueExpression)
        .append(".size() != expected")
        .append(toPascalCase(ownerName))
        .append("Count) {\n")
        .append("    throw std::invalid_argument(\"")
        .append(ownerName)
        .append(" length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("  }\n");
  }

  /**
   * Appends varString encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the string value
   * @param resolvedVarString varString definition
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param fieldName field/member name used in helper labels
   */
  private static void appendEncodeVarString(
      StringBuilder builder,
      String valueExpression,
      ResolvedVarString resolvedVarString,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String fieldName) {
    String encodedLocalName = "encoded" + toPascalCase(fieldName);
    builder
        .append("  {\n")
        .append("    std::string ")
        .append(encodedLocalName)
        .append(" = ")
        .append(valueExpression)
        .append(";\n");
    appendVarStringCountValidation(
        builder,
        resolvedVarString.lengthMode(),
        encodedLocalName,
        primitiveFieldByName,
        ownerPrefix,
        fieldName);
    builder
        .append("    out.insert(out.end(), ")
        .append(encodedLocalName)
        .append(".begin(), ")
        .append(encodedLocalName)
        .append(".end());\n");
    String terminatorLiteral = terminatorLiteral(resolvedVarString.lengthMode());
    if (terminatorLiteral != null) {
      appendEncodePrimitive(
          builder,
          primitiveLiteralExpression(PrimitiveType.UINT8, parseNumericLiteral(terminatorLiteral)),
          PrimitiveType.UINT8,
          Endian.BIG,
          fieldName + "_terminator");
    }
    builder.append("  }\n");
  }

  /**
   * Appends count-field validation for varString encode paths.
   *
   * @param builder destination source builder
   * @param lengthMode varString length mode
   * @param encodedBytesExpression expression that resolves to the encoded byte string
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for count-field references
   * @param fieldName field/member name used in helper labels
   */
  private static void appendVarStringCountValidation(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      String encodedBytesExpression,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String fieldName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }
    PrimitiveType countType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    if (countType == null) {
      return;
    }
    builder
        .append("    std::size_t expected")
        .append(toPascalCase(fieldName))
        .append("Length = requireCount(")
        .append(ownerPrefix)
        .append(resolvedCountFieldLength.ref())
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    if (")
        .append(encodedBytesExpression)
        .append(".size() != expected")
        .append(toPascalCase(fieldName))
        .append("Length) {\n")
        .append("      throw std::invalid_argument(\"")
        .append(fieldName)
        .append(" byte length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("    }\n");
  }

  /**
   * Appends pad encode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  private static void appendEncodePad(StringBuilder builder, ResolvedPad resolvedPad) {
    builder
        .append("  for (std::size_t padIndex = 0; padIndex < ")
        .append(resolvedPad.bytes())
        .append("U; padIndex++) {\n")
        .append("    out.push_back(0U);\n")
        .append("  }\n");
  }

  /**
   * Appends checksum encode statements.
   *
   * @param builder destination source builder
   * @param resolvedChecksum checksum definition
   */
  private static void appendEncodeChecksum(
      StringBuilder builder, ResolvedChecksum resolvedChecksum) {
    ChecksumRangeRules.ChecksumRange checksumRange =
        requiredChecksumRange(resolvedChecksum.range());
    String algorithm = resolvedChecksum.algorithm();
    builder
        .append("  {\n")
        .append("    validateChecksumRange(out.size(), ")
        .append(checksumRange.startInclusive())
        .append(", ")
        .append(checksumRange.endInclusive())
        .append(", \"")
        .append(algorithm)
        .append("\", \"")
        .append(resolvedChecksum.range())
        .append("\");\n")
        .append("    std::span<const std::uint8_t> checksumSource(out.data(), out.size());\n");
    if ("crc16".equals(algorithm)) {
      builder
          .append("    writeIntegral<std::uint16_t>(out, crc16(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append("), false);\n");
    } else if ("crc32".equals(algorithm)) {
      builder
          .append("    writeIntegral<std::uint32_t>(out, crc32(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append("), false);\n");
    } else if ("crc64".equals(algorithm)) {
      builder
          .append("    writeIntegral<std::uint64_t>(out, crc64(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append("), false);\n");
    } else if ("sha256".equals(algorithm)) {
      builder
          .append("    std::array<std::uint8_t, 32> checksumValue = sha256(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    out.insert(out.end(), checksumValue.begin(), checksumValue.end());\n");
    } else {
      throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm);
    }
    builder.append("  }\n");
  }

  /**
   * Appends conditional-block encode statements.
   *
   * @param builder destination source builder
   * @param resolvedIfBlock conditional block definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner prefix used for field access
   */
  private static void appendEncodeIfBlock(
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
    appendEncodeMembers(
        builder,
        resolvedIfBlock.members(),
        messageType,
        generationContext,
        primitiveFieldByName,
        ownerPrefix);
    builder.append("  }\n");
  }
}
