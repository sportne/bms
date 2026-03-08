package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.*;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.codegen.common.LengthModeRules;
import io.github.sportne.bms.codegen.java.JavaCodeGenerator.GenerationContext;
import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
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
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** Emits Java encode method bodies for generated message classes. */
final class JavaEncodeEmitter {
  /** Prevents instantiation of this static utility class. */
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
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);

    builder.append("  public byte[] encode() {\n");
    builder.append("    ByteArrayOutputStream out = new ByteArrayOutputStream();\n");

    appendEncodeMembers(
        builder, messageType.members(), messageType, generationContext, primitiveFieldByName);

    builder.append("    return out.toByteArray();\n");
    builder.append("  }\n\n");
  }

  /**
   * Appends encode statements for one member list recursively.
   *
   * @param builder destination source builder
   * @param members members to encode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendEncodeMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    for (ResolvedMessageMember member : members) {
      appendEncodeMember(builder, member, messageType, generationContext, primitiveFieldByName);
    }
  }

  /**
   * Appends encode statements for one member.
   *
   * @param builder destination source builder
   * @param member member to encode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendEncodeMember(
      StringBuilder builder,
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    if (member instanceof ResolvedField resolvedField) {
      appendEncodeField(
          builder, resolvedField, messageType, generationContext, primitiveFieldByName);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      appendEncodeBitField(builder, resolvedBitField);
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      appendEncodeFloat(
          builder, "this." + resolvedFloat.name(), resolvedFloat, resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      appendEncodeScaledInt(
          builder, "this." + resolvedScaledInt.name(), resolvedScaledInt, resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      appendEncodeArray(
          builder,
          "this." + resolvedArray.name(),
          resolvedArray,
          resolvedArray.name(),
          generationContext);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      appendEncodeVector(
          builder,
          "this." + resolvedVector.name(),
          resolvedVector,
          resolvedVector.name(),
          generationContext,
          primitiveFieldByName,
          "this.");
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      appendEncodeBlobArray(
          builder, "this." + resolvedBlobArray.name(), resolvedBlobArray, resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      appendEncodeBlobVector(
          builder,
          "this." + resolvedBlobVector.name(),
          resolvedBlobVector,
          resolvedBlobVector.name(),
          primitiveFieldByName,
          "this.");
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      appendEncodeVarString(
          builder,
          "this." + resolvedVarString.name(),
          resolvedVarString,
          resolvedVarString.name(),
          primitiveFieldByName,
          "this.");
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
          builder, resolvedIfBlock, messageType, generationContext, primitiveFieldByName);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendEncodeMembers(
          builder,
          resolvedNestedType.members(),
          messageType,
          generationContext,
          primitiveFieldByName);
      return;
    }

    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }

  /**
   * Appends encode statements for one field member.
   *
   * @param builder destination source builder
   * @param resolvedField field member to encode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendEncodeField(
      StringBuilder builder,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    String fieldExpression = "this." + resolvedField.name();

    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendPrimitiveEncode(
          builder, fieldExpression, primitiveTypeRef.primitiveType(), resolvedField.endian());
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, generationContext);
      appendMessageEncode(builder, fieldExpression, resolvedField.name(), javaType);
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      appendEncodeFloat(builder, fieldExpression, resolvedFloat, resolvedField.name());
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      appendEncodeScaledInt(builder, fieldExpression, resolvedScaledInt, resolvedField.name());
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      appendEncodeArray(
          builder, fieldExpression, resolvedArray, resolvedField.name(), generationContext);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      appendEncodeVector(
          builder,
          fieldExpression,
          resolvedVector,
          resolvedField.name(),
          generationContext,
          primitiveFieldByName,
          "this.");
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      ResolvedBlobArray resolvedBlobArray =
          generationContext.reusableBlobArrayByName().get(blobArrayTypeRef.blobArrayTypeName());
      appendEncodeBlobArray(builder, fieldExpression, resolvedBlobArray, resolvedField.name());
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      appendEncodeBlobVector(
          builder,
          fieldExpression,
          resolvedBlobVector,
          resolvedField.name(),
          primitiveFieldByName,
          "this.");
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName());
      appendEncodeVarString(
          builder,
          fieldExpression,
          resolvedVarString,
          resolvedField.name(),
          primitiveFieldByName,
          "this.");
      return;
    }

    throw new IllegalStateException(
        "Unsupported type reference: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Appends primitive encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the value to encode
   * @param primitiveType primitive type of the value
   * @param endian optional endian override
   */
  private static void appendPrimitiveEncode(
      StringBuilder builder, String valueExpression, PrimitiveType primitiveType, Endian endian) {
    String order = byteOrderExpression(endian);
    switch (primitiveType) {
      case UINT8 -> builder.append("    writeUInt8(out, ").append(valueExpression).append(");\n");
      case UINT16 -> builder
          .append("    writeUInt16(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case UINT32 -> builder
          .append("    writeUInt32(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case UINT64 -> builder
          .append("    writeUInt64(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case INT8 -> builder.append("    writeInt8(out, ").append(valueExpression).append(");\n");
      case INT16 -> builder
          .append("    writeInt16(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case INT32 -> builder
          .append("    writeInt32(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case INT64 -> builder
          .append("    writeInt64(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
    }
  }

  /**
   * Appends nested-message encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the nested message value
   * @param fieldName field name used in null-check text
   * @param javaType nested Java type name used to build temp variable names
   */
  private static void appendMessageEncode(
      StringBuilder builder, String valueExpression, String fieldName, String javaType) {
    String variableSuffix = toPascalCase(javaType);
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    byte[] encoded")
        .append(variableSuffix)
        .append(" = ")
        .append(valueExpression)
        .append(".encode();\n")
        .append("    out.write(encoded")
        .append(variableSuffix)
        .append(", 0, encoded")
        .append(variableSuffix)
        .append(".length);\n");
  }

  /**
   * Appends bitField encode statements.
   *
   * @param builder destination source builder
   * @param resolvedBitField bitField member to encode
   */
  private static void appendEncodeBitField(
      StringBuilder builder, ResolvedBitField resolvedBitField) {
    PrimitiveType primitiveType = primitiveTypeForBitFieldSize(resolvedBitField.size());
    appendPrimitiveEncode(
        builder, "this." + resolvedBitField.name(), primitiveType, resolvedBitField.endian());
  }

  /**
   * Resolves the primitive wire type used by one bitField storage size.
   *
   * @param bitFieldSize bitField storage size
   * @return primitive wire type used to encode/decode bitField storage
   */
  private static PrimitiveType primitiveTypeForBitFieldSize(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> PrimitiveType.UINT8;
      case U16 -> PrimitiveType.UINT16;
      case U32 -> PrimitiveType.UINT32;
      case U64 -> PrimitiveType.UINT64;
    };
  }

  /**
   * Appends float encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the float logical value
   * @param resolvedFloat float definition
   * @param fieldName field/member name for exception messages
   */
  private static void appendEncodeFloat(
      StringBuilder builder,
      String valueExpression,
      ResolvedFloat resolvedFloat,
      String fieldName) {
    String order = byteOrderExpression(resolvedFloat.endian());
    String scaleLiteral = decimalLiteral(resolvedFloat.scale());

    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      switch (resolvedFloat.size()) {
        case F16 -> builder
            .append("    writeUInt16(out, Short.toUnsignedInt(Float.floatToFloat16((float) ")
            .append(valueExpression)
            .append(")), ")
            .append(order)
            .append(");\n");
        case F32 -> builder
            .append("    writeInt32(out, Float.floatToIntBits((float) ")
            .append(valueExpression)
            .append("), ")
            .append(order)
            .append(");\n");
        case F64 -> builder
            .append("    writeInt64(out, Double.doubleToLongBits(")
            .append(valueExpression)
            .append("), ")
            .append(order)
            .append(");\n");
      }
      return;
    }

    switch (resolvedFloat.size()) {
      case F16 -> builder
          .append("    writeInt16(out, (short) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -32768L, 32767L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case F32 -> builder
          .append("    writeInt32(out, (int) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -2147483648L, 2147483647L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case F64 -> builder
          .append("    writeInt64(out, scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", Long.MIN_VALUE, Long.MAX_VALUE, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
    }
  }

  /**
   * Appends scaled-int encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the logical scaled value
   * @param resolvedScaledInt scaled-int definition
   * @param fieldName field/member name for exception messages
   */
  private static void appendEncodeScaledInt(
      StringBuilder builder,
      String valueExpression,
      ResolvedScaledInt resolvedScaledInt,
      String fieldName) {
    String order = byteOrderExpression(resolvedScaledInt.endian());
    String scaleLiteral = decimalLiteral(resolvedScaledInt.scale());

    switch (resolvedScaledInt.baseType()) {
      case INT8 -> builder
          .append("    writeInt8(out, (byte) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -128L, 127L, \"")
          .append(fieldName)
          .append("\"));\n");
      case UINT8 -> builder
          .append("    writeUInt8(out, (short) scaleToUnsignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", 255L, \"")
          .append(fieldName)
          .append("\"));\n");
      case INT16 -> builder
          .append("    writeInt16(out, (short) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -32768L, 32767L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case UINT16 -> builder
          .append("    writeUInt16(out, (int) scaleToUnsignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", 65535L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case INT32 -> builder
          .append("    writeInt32(out, (int) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -2147483648L, 2147483647L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case UINT32 -> builder
          .append("    writeUInt32(out, scaleToUnsignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", 4294967295L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case INT64 -> builder
          .append("    writeInt64(out, scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", Long.MIN_VALUE, Long.MAX_VALUE, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case UINT64 -> builder
          .append("    writeUInt64(out, scaleToUnsignedRaw64(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
    }
  }

  /**
   * Appends fixed-array encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the array value
   * @param resolvedArray array definition
   * @param fieldName field/member name for exception text
   * @param generationContext reusable lookup maps
   */
  private static void appendEncodeArray(
      StringBuilder builder,
      String valueExpression,
      ResolvedArray resolvedArray,
      String fieldName,
      GenerationContext generationContext) {
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);

    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(resolvedArray.length())
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" must contain exactly ")
        .append(resolvedArray.length())
        .append(" elements.\");\n")
        .append("    }\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(javaElementTypeForCollection(resolvedArray.elementTypeRef(), generationContext))
        .append(' ')
        .append(loopItemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndexName)
        .append("];\n");

    appendEncodeCollectionElement(
        builder,
        loopItemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        generationContext);

    builder.append("    }\n");
  }

  /**
   * Appends vector encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the vector value
   * @param resolvedVector vector definition
   * @param fieldName field/member name for exception text
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendEncodeVector(
      StringBuilder builder,
      String valueExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);

    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n");

    appendVectorCountValidation(
        builder,
        resolvedVector.lengthMode(),
        valueExpression,
        primitiveFieldByName,
        ownerPrefix,
        fieldName);

    builder
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(valueExpression)
        .append(".length; ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext))
        .append(' ')
        .append(loopItemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndexName)
        .append("];\n");

    appendEncodeCollectionElement(
        builder,
        loopItemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        generationContext);

    builder.append("    }\n");

    appendTerminatorEncode(
        builder,
        resolvedVector.lengthMode(),
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        generationContext);
  }

  /**
   * Appends count-field validation for vector/blobVector encoders.
   *
   * @param builder destination source builder
   * @param lengthMode vector/blob length mode
   * @param valueExpression expression that resolves to vector/blob value
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   * @param fieldName field/member name for exception text
   */
  private static void appendVectorCountValidation(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      String valueExpression,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String fieldName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }

    PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String localName = "expected" + toPascalCase(fieldName) + "Count";

    builder
        .append("    int ")
        .append(localName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(localName)
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("    }\n");
  }

  /**
   * Appends terminator write statements for vector encoders.
   *
   * @param builder destination source builder
   * @param lengthMode vector length mode
   * @param elementTypeRef vector element type
   * @param endian optional vector endian override
   * @param generationContext reusable lookup maps
   */
  private static void appendTerminatorEncode(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      GenerationContext generationContext) {
    String terminatorLiteral = terminatorLiteral(lengthMode);
    if (terminatorLiteral == null) {
      return;
    }

    BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
    PrimitiveType primitiveType = ((PrimitiveTypeRef) elementTypeRef).primitiveType();
    String terminatorExpression = primitiveLiteralExpression(primitiveType, numericLiteral);

    appendEncodeCollectionElement(
        builder, terminatorExpression, elementTypeRef, endian, generationContext);
  }

  /**
   * Appends one collection element encode statement.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to one element value
   * @param elementTypeRef collection element type reference
   * @param endian optional endian override from the collection definition
   * @param generationContext reusable lookup maps
   */
  private static void appendEncodeCollectionElement(
      StringBuilder builder,
      String valueExpression,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendPrimitiveEncode(builder, valueExpression, primitiveTypeRef.primitiveType(), endian);
      return;
    }

    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, generationContext);
      appendMessageEncode(builder, valueExpression, valueExpression, javaType);
      return;
    }

    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      appendEncodeFloat(builder, valueExpression, resolvedFloat, valueExpression);
      return;
    }

    ResolvedScaledInt resolvedScaledInt =
        generationContext
            .reusableScaledIntByName()
            .get(((ScaledIntTypeRef) elementTypeRef).scaledIntTypeName());
    appendEncodeScaledInt(builder, valueExpression, resolvedScaledInt, valueExpression);
  }

  /**
   * Appends blobArray encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob value
   * @param resolvedBlobArray blobArray definition
   * @param fieldName field/member name for exception text
   */
  private static void appendEncodeBlobArray(
      StringBuilder builder,
      String valueExpression,
      ResolvedBlobArray resolvedBlobArray,
      String fieldName) {
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(resolvedBlobArray.length())
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" must contain exactly ")
        .append(resolvedBlobArray.length())
        .append(" bytes.\");\n")
        .append("    }\n")
        .append("    out.write(")
        .append(valueExpression)
        .append(", 0, ")
        .append(resolvedBlobArray.length())
        .append(");\n");
  }

  /**
   * Appends blobVector encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob value
   * @param resolvedBlobVector blobVector definition
   * @param fieldName field/member name for exception text
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendEncodeBlobVector(
      StringBuilder builder,
      String valueExpression,
      ResolvedBlobVector resolvedBlobVector,
      String fieldName,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n");

    appendVectorCountValidation(
        builder,
        resolvedBlobVector.lengthMode(),
        valueExpression,
        primitiveFieldByName,
        ownerPrefix,
        fieldName);

    builder
        .append("    out.write(")
        .append(valueExpression)
        .append(", 0, ")
        .append(valueExpression)
        .append(".length);\n");

    String terminatorLiteral = terminatorLiteral(resolvedBlobVector.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      builder.append("    writeUInt8(out, (short) ").append(numericLiteral).append(");\n");
    }
  }

  /**
   * Appends varString encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the string value
   * @param resolvedVarString varString definition
   * @param fieldName field/member name for exception text
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendEncodeVarString(
      StringBuilder builder,
      String valueExpression,
      ResolvedVarString resolvedVarString,
      String fieldName,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String encodedLocalName = "encoded" + toPascalCase(fieldName);
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    byte[] ")
        .append(encodedLocalName)
        .append(" = ")
        .append(valueExpression)
        .append(".getBytes(")
        .append(charsetExpression(resolvedVarString.encoding()))
        .append(");\n");

    appendVarStringCountValidation(
        builder,
        resolvedVarString.lengthMode(),
        encodedLocalName,
        primitiveFieldByName,
        ownerPrefix,
        fieldName);

    builder
        .append("    out.write(")
        .append(encodedLocalName)
        .append(", 0, ")
        .append(encodedLocalName)
        .append(".length);\n");

    String terminatorLiteral = terminatorLiteral(resolvedVarString.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      builder.append("    writeUInt8(out, (short) ").append(numericLiteral).append(");\n");
    }
  }

  /**
   * Appends count-field validation for varString encoders.
   *
   * @param builder destination source builder
   * @param lengthMode varString length mode
   * @param encodedBytesExpression expression that resolves to encoded bytes
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   * @param fieldName field/member name for exception text
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

    PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String localName = "expected" + toPascalCase(fieldName) + "Length";

    builder
        .append("    int ")
        .append(localName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    if (")
        .append(encodedBytesExpression)
        .append(".length != ")
        .append(localName)
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
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
        .append("    for (int padIndex = 0; padIndex < ")
        .append(resolvedPad.bytes())
        .append("; padIndex++) {\n")
        .append("      out.write(0);\n")
        .append("    }\n");
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
        .append("    {\n")
        .append("      byte[] checksumSource = out.toByteArray();\n")
        .append("      validateChecksumRange(checksumSource.length, ")
        .append(checksumRange.startInclusive())
        .append(", ")
        .append(checksumRange.endInclusive())
        .append(", \"")
        .append(resolvedChecksum.algorithm())
        .append("\", \"")
        .append(resolvedChecksum.range())
        .append("\");\n");

    if ("crc16".equals(algorithm)) {
      builder
          .append("      int checksumValue = crc16(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      writeUInt16(out, checksumValue, ByteOrder.BIG_ENDIAN);\n");
    } else if ("crc32".equals(algorithm)) {
      builder
          .append("      long checksumValue = crc32(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      writeUInt32(out, checksumValue, ByteOrder.BIG_ENDIAN);\n");
    } else if ("crc64".equals(algorithm)) {
      builder
          .append("      long checksumValue = crc64(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      writeUInt64(out, checksumValue, ByteOrder.BIG_ENDIAN);\n");
    } else if ("sha256".equals(algorithm)) {
      builder
          .append("      byte[] checksumValue = sha256(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      out.write(checksumValue, 0, checksumValue.length);\n");
    } else {
      throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm);
    }
    builder.append("    }\n");
  }

  /**
   * Appends conditional-block encode statements.
   *
   * @param builder destination source builder
   * @param resolvedIfBlock if-block definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendEncodeIfBlock(
      StringBuilder builder,
      ResolvedIfBlock resolvedIfBlock,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    String conditionExpression = ifConditionExpression(resolvedIfBlock.condition(), "this.");
    builder.append("    if (").append(conditionExpression).append(") {\n");
    appendEncodeMembers(
        builder, resolvedIfBlock.members(), messageType, generationContext, primitiveFieldByName);
    builder.append("    }\n");
  }

  /**
   * Resolves optional terminator literal from a length mode.
   *
   * @param lengthMode vector/blob length mode
   * @return terminator literal when present, otherwise {@code null}
   */
  private static String terminatorLiteral(ResolvedLengthMode lengthMode) {
    if (lengthMode instanceof ResolvedTerminatorValueLength
        || lengthMode instanceof ResolvedTerminatorField) {
      return LengthModeRules.terminatorLiteral(lengthMode);
    }
    return null;
  }

  /**
   * Appends decode methods.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
}
