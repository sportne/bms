package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.*;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.codegen.java.JavaCodeGenerator.GenerationContext;
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

/** Emits Java decode method bodies for generated message classes. */
final class JavaDecodeEmitter {
  /** Prevents instantiation of this static utility class. */
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

    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    appendDecodeMembers(
        builder, messageType.members(), messageType, generationContext, primitiveFieldByName);

    builder.append("    return value;\n");
    builder.append("  }\n\n");
  }

  /**
   * Appends decode statements for one member list recursively.
   *
   * @param builder destination source builder
   * @param members members to decode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendDecodeMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    for (ResolvedMessageMember member : members) {
      appendDecodeMember(builder, member, messageType, generationContext, primitiveFieldByName);
    }
  }

  /**
   * Appends decode statements for one member.
   *
   * @param builder destination source builder
   * @param member member to decode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendDecodeMember(
      StringBuilder builder,
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    if (member instanceof ResolvedField resolvedField) {
      appendDecodeField(
          builder, resolvedField, messageType, generationContext, primitiveFieldByName);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      appendDecodeBitField(builder, resolvedBitField);
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      appendDecodeFloat(builder, "value." + resolvedFloat.name(), resolvedFloat);
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      appendDecodeScaledInt(builder, "value." + resolvedScaledInt.name(), resolvedScaledInt);
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      appendDecodeArray(
          builder,
          "value." + resolvedArray.name(),
          resolvedArray,
          resolvedArray.name(),
          generationContext);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      appendDecodeVector(
          builder,
          "value." + resolvedVector.name(),
          resolvedVector,
          resolvedVector.name(),
          generationContext,
          primitiveFieldByName,
          "value.");
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      appendDecodeBlobArray(builder, "value." + resolvedBlobArray.name(), resolvedBlobArray);
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      appendDecodeBlobVector(
          builder,
          "value." + resolvedBlobVector.name(),
          resolvedBlobVector,
          resolvedBlobVector.name(),
          primitiveFieldByName,
          "value.");
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      appendDecodeVarString(
          builder,
          "value." + resolvedVarString.name(),
          resolvedVarString,
          resolvedVarString.name(),
          primitiveFieldByName,
          "value.");
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
          builder, resolvedIfBlock, messageType, generationContext, primitiveFieldByName);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendDecodeMembers(
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
   * Appends decode statements for one field member.
   *
   * @param builder destination source builder
   * @param resolvedField field member to decode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendDecodeField(
      StringBuilder builder,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    String targetExpression = "value." + resolvedField.name();

    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendPrimitiveDecode(
          builder, targetExpression, primitiveTypeRef.primitiveType(), resolvedField.endian());
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, generationContext);
      builder
          .append("    ")
          .append(targetExpression)
          .append(" = ")
          .append(javaType)
          .append(".decode(input);\n");
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      appendDecodeFloat(builder, targetExpression, resolvedFloat);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      appendDecodeScaledInt(builder, targetExpression, resolvedScaledInt);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      appendDecodeArray(
          builder, targetExpression, resolvedArray, resolvedField.name(), generationContext);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      appendDecodeVector(
          builder,
          targetExpression,
          resolvedVector,
          resolvedField.name(),
          generationContext,
          primitiveFieldByName,
          "value.");
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      ResolvedBlobArray resolvedBlobArray =
          generationContext.reusableBlobArrayByName().get(blobArrayTypeRef.blobArrayTypeName());
      appendDecodeBlobArray(builder, targetExpression, resolvedBlobArray);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      appendDecodeBlobVector(
          builder,
          targetExpression,
          resolvedBlobVector,
          resolvedField.name(),
          primitiveFieldByName,
          "value.");
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName());
      appendDecodeVarString(
          builder,
          targetExpression,
          resolvedVarString,
          resolvedField.name(),
          primitiveFieldByName,
          "value.");
      return;
    }

    throw new IllegalStateException(
        "Unsupported type reference: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Appends primitive decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param primitiveType primitive type to decode
   * @param endian optional endian override
   */
  private static void appendPrimitiveDecode(
      StringBuilder builder, String targetExpression, PrimitiveType primitiveType, Endian endian) {
    String order = byteOrderExpression(endian);
    switch (primitiveType) {
      case UINT8 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt8(input);\n");
      case UINT16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt16(input, ")
          .append(order)
          .append(");\n");
      case UINT32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt32(input, ")
          .append(order)
          .append(");\n");
      case UINT64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt64(input, ")
          .append(order)
          .append(");\n");
      case INT8 -> builder.append("    ").append(targetExpression).append(" = readInt8(input);\n");
      case INT16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt16(input, ")
          .append(order)
          .append(");\n");
      case INT32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt32(input, ")
          .append(order)
          .append(");\n");
      case INT64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt64(input, ")
          .append(order)
          .append(");\n");
    }
  }

  /**
   * Appends bitField decode statements.
   *
   * @param builder destination source builder
   * @param resolvedBitField bitField member to decode
   */
  private static void appendDecodeBitField(
      StringBuilder builder, ResolvedBitField resolvedBitField) {
    PrimitiveType primitiveType = primitiveTypeForBitFieldSize(resolvedBitField.size());
    appendPrimitiveDecode(
        builder, "value." + resolvedBitField.name(), primitiveType, resolvedBitField.endian());
  }

  /**
   * Appends float decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedFloat float definition
   */
  private static void appendDecodeFloat(
      StringBuilder builder, String targetExpression, ResolvedFloat resolvedFloat) {
    String order = byteOrderExpression(resolvedFloat.endian());
    String scaleLiteral = decimalLiteral(resolvedFloat.scale());

    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      switch (resolvedFloat.size()) {
        case F16 -> builder
            .append("    ")
            .append(targetExpression)
            .append(" = Float.float16ToFloat((short) readUInt16(input, ")
            .append(order)
            .append("));\n");
        case F32 -> builder
            .append("    ")
            .append(targetExpression)
            .append(" = Float.intBitsToFloat(readInt32(input, ")
            .append(order)
            .append("));\n");
        case F64 -> builder
            .append("    ")
            .append(targetExpression)
            .append(" = Double.longBitsToDouble(readInt64(input, ")
            .append(order)
            .append("));\n");
      }
      return;
    }

    switch (resolvedFloat.size()) {
      case F16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt16(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case F32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt32(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case F64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt64(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
    }
  }

  /**
   * Appends scaled-int decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedScaledInt scaled-int definition
   */
  private static void appendDecodeScaledInt(
      StringBuilder builder, String targetExpression, ResolvedScaledInt resolvedScaledInt) {
    String order = byteOrderExpression(resolvedScaledInt.endian());
    String scaleLiteral = decimalLiteral(resolvedScaledInt.scale());

    switch (resolvedScaledInt.baseType()) {
      case INT8 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt8(input) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT8 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt8(input) * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt16(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt16(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt32(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt32(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt64(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = unsignedLongToDouble(readUInt64(input, ")
          .append(order)
          .append(")) * ")
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
   * @param fieldName field/member name for local variables
   * @param generationContext reusable lookup maps
   */
  private static void appendDecodeArray(
      StringBuilder builder,
      String targetExpression,
      ResolvedArray resolvedArray,
      String fieldName,
      GenerationContext generationContext) {
    String loopIndexName = toLoopIndexName(fieldName);
    String loopItemName = toLoopItemName(fieldName);
    String elementType =
        javaElementTypeForCollection(resolvedArray.elementTypeRef(), generationContext);

    builder
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(resolvedArray.length())
        .append("];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n");

    appendDecodeCollectionElement(
        builder,
        loopItemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        generationContext);

    builder
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(loopItemName)
        .append(";\n")
        .append("    }\n");
  }

  /**
   * Appends vector decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendDecodeVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (resolvedVector.lengthMode() instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      appendDecodeCountedVector(
          builder,
          targetExpression,
          resolvedVector,
          fieldName,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          resolvedCountFieldLength);
      return;
    }

    appendDecodeTerminatedVector(
        builder,
        targetExpression,
        resolvedVector,
        fieldName,
        generationContext,
        terminatorLiteral(resolvedVector.lengthMode()));
  }

  /**
   * Appends decode statements for count-based vectors.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   * @param resolvedCountFieldLength count-field length mode
   */
  private static void appendDecodeCountedVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      ResolvedCountFieldLength resolvedCountFieldLength) {
    String countLocalName = "expected" + toPascalCase(fieldName) + "Count";
    PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String elementType =
        javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext);
    String loopIndexName = toLoopIndexName(fieldName);
    String loopItemName = toLoopItemName(fieldName);

    builder
        .append("    int ")
        .append(countLocalName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(countLocalName)
        .append("];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(countLocalName)
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n");

    appendDecodeCollectionElement(
        builder,
        loopItemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        generationContext);

    builder
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(loopItemName)
        .append(";\n")
        .append("    }\n");
  }

  /**
   * Appends decode statements for terminator-based vectors.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   * @param generationContext reusable lookup maps
   * @param literal terminator literal text
   */
  private static void appendDecodeTerminatedVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      GenerationContext generationContext,
      String literal) {
    PrimitiveType primitiveType =
        ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
    BigInteger numericLiteral = parseNumericLiteral(literal);

    String listName = toLoopItemName(fieldName) + "List";
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);
    String wrapperType = wrapperTypeForPrimitive(primitiveType);
    String elementType =
        javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext);

    builder
        .append("    ArrayList<")
        .append(wrapperType)
        .append("> ")
        .append(listName)
        .append(" = new ArrayList<>();\n")
        .append("    while (true) {\n");

    appendDecodeCollectionElement(
        builder,
        loopItemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        generationContext);

    builder
        .append("      if (")
        .append(terminatorComparisonExpression(loopItemName, primitiveType, numericLiteral))
        .append(") {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(listName)
        .append(".add(")
        .append(loopItemName)
        .append(");\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(listName)
        .append(".size()];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(listName)
        .append(".size(); ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(listName)
        .append(".get(")
        .append(loopIndexName)
        .append(");\n")
        .append("    }\n");
  }

  /**
   * Appends one collection element decode statement.
   *
   * @param builder destination source builder
   * @param targetLocalName local variable name that receives decoded element value
   * @param elementTypeRef collection element type reference
   * @param endian optional endian override from the collection definition
   * @param generationContext reusable lookup maps
   */
  private static void appendDecodeCollectionElement(
      StringBuilder builder,
      String targetLocalName,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      builder
          .append("      ")
          .append(primitiveTypeRef.primitiveType().javaTypeName())
          .append(' ')
          .append(targetLocalName)
          .append(" = ")
          .append(primitiveDecodeExpression(primitiveTypeRef.primitiveType(), endian))
          .append(";\n");
      return;
    }

    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, generationContext);
      builder
          .append("      ")
          .append(javaType)
          .append(' ')
          .append(targetLocalName)
          .append(" = ")
          .append(javaType)
          .append(".decode(input);\n");
      return;
    }

    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      builder.append("      double ").append(targetLocalName).append(";\n");
      appendDecodeFloat(builder, targetLocalName, resolvedFloat);
      return;
    }

    ResolvedScaledInt resolvedScaledInt =
        generationContext
            .reusableScaledIntByName()
            .get(((ScaledIntTypeRef) elementTypeRef).scaledIntTypeName());
    builder.append("      double ").append(targetLocalName).append(";\n");
    appendDecodeScaledInt(builder, targetLocalName, resolvedScaledInt);
  }

  /**
   * Appends blobArray decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedBlobArray blobArray definition
   */
  private static void appendDecodeBlobArray(
      StringBuilder builder, String targetExpression, ResolvedBlobArray resolvedBlobArray) {
    builder
        .append("    ")
        .append(targetExpression)
        .append(" = new byte[")
        .append(resolvedBlobArray.length())
        .append("];\n")
        .append("    input.get(")
        .append(targetExpression)
        .append(");\n");
  }

  /**
   * Appends blobVector decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedBlobVector blobVector definition
   * @param fieldName field/member name for local variables
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendDecodeBlobVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedBlobVector resolvedBlobVector,
      String fieldName,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (resolvedBlobVector.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      String countLocalName = "expected" + toPascalCase(fieldName) + "Count";
      PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
      String countExpression =
          countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
      String countMethod =
          countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";

      builder
          .append("    int ")
          .append(countLocalName)
          .append(" = ")
          .append(countMethod)
          .append('(')
          .append(countExpression)
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    ")
          .append(targetExpression)
          .append(" = new byte[")
          .append(countLocalName)
          .append("];\n")
          .append("    input.get(")
          .append(targetExpression)
          .append(");\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedBlobVector.lengthMode()));
    String tempName = toLoopItemName(fieldName) + "Buffer";
    builder
        .append("    ByteArrayOutputStream ")
        .append(tempName)
        .append(" = new ByteArrayOutputStream();\n")
        .append("    while (true) {\n")
        .append("      short nextByte = readUInt8(input);\n")
        .append("      if ((nextByte & 0xFFL) == ")
        .append(numericLiteral)
        .append("L) {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(tempName)
        .append(".write(nextByte & 0xFF);\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = ")
        .append(tempName)
        .append(".toByteArray();\n");
  }

  /**
   * Appends varString decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVarString varString definition
   * @param fieldName field/member name for local variables
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendDecodeVarString(
      StringBuilder builder,
      String targetExpression,
      ResolvedVarString resolvedVarString,
      String fieldName,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String charsetExpression = charsetExpression(resolvedVarString.encoding());
    if (resolvedVarString.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      String countLocalName = "expected" + toPascalCase(fieldName) + "Length";
      String bytesLocalName = "bytes" + toPascalCase(fieldName);
      PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
      String countExpression =
          countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
      String countMethod =
          countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";

      builder
          .append("    int ")
          .append(countLocalName)
          .append(" = ")
          .append(countMethod)
          .append('(')
          .append(countExpression)
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    byte[] ")
          .append(bytesLocalName)
          .append(" = new byte[")
          .append(countLocalName)
          .append("];\n")
          .append("    input.get(")
          .append(bytesLocalName)
          .append(");\n")
          .append("    ")
          .append(targetExpression)
          .append(" = new String(")
          .append(bytesLocalName)
          .append(", ")
          .append(charsetExpression)
          .append(");\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedVarString.lengthMode()));
    String tempName = "bytes" + toPascalCase(fieldName) + "Buffer";
    builder
        .append("    ByteArrayOutputStream ")
        .append(tempName)
        .append(" = new ByteArrayOutputStream();\n")
        .append("    while (true) {\n")
        .append("      short nextByte = readUInt8(input);\n")
        .append("      if ((nextByte & 0xFFL) == ")
        .append(numericLiteral)
        .append("L) {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(tempName)
        .append(".write(nextByte & 0xFF);\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = ")
        .append(tempName)
        .append(".toString(")
        .append(charsetExpression)
        .append(");\n");
  }

  /**
   * Appends pad decode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  private static void appendDecodePad(StringBuilder builder, ResolvedPad resolvedPad) {
    builder
        .append("    input.position(input.position() + ")
        .append(resolvedPad.bytes())
        .append(");\n");
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
        .append("    {\n")
        .append("      validateChecksumRange(input.limit() - messageStartPosition, ")
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
          .append("      int expectedChecksum = readUInt16(input, ByteOrder.BIG_ENDIAN);\n")
          .append("      int actualChecksum = crc16(input, messageStartPosition, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      if (expectedChecksum != actualChecksum) {\n")
          .append("        throw new IllegalArgumentException(\"Checksum mismatch for crc16 range ")
          .append(resolvedChecksum.range())
          .append(". Expected \" + expectedChecksum + \", computed \" + actualChecksum + '.');\n")
          .append("      }\n");
    } else if ("crc32".equals(algorithm)) {
      builder
          .append("      long expectedChecksum = readUInt32(input, ByteOrder.BIG_ENDIAN);\n")
          .append("      long actualChecksum = crc32(input, messageStartPosition, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      if (expectedChecksum != actualChecksum) {\n")
          .append("        throw new IllegalArgumentException(\"Checksum mismatch for crc32 range ")
          .append(resolvedChecksum.range())
          .append(". Expected \" + expectedChecksum + \", computed \" + actualChecksum + '.');\n")
          .append("      }\n");
    } else if ("crc64".equals(algorithm)) {
      builder
          .append("      long expectedChecksum = readUInt64(input, ByteOrder.BIG_ENDIAN);\n")
          .append("      long actualChecksum = crc64(input, messageStartPosition, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      if (Long.compareUnsigned(expectedChecksum, actualChecksum) != 0) {\n")
          .append("        throw new IllegalArgumentException(\"Checksum mismatch for crc64 range ")
          .append(resolvedChecksum.range())
          .append(". Expected \" + Long.toUnsignedString(expectedChecksum)")
          .append(" + \", computed \" + Long.toUnsignedString(actualChecksum) + '.');\n")
          .append("      }\n");
    } else if ("sha256".equals(algorithm)) {
      builder
          .append("      byte[] expectedChecksum = new byte[32];\n")
          .append("      input.get(expectedChecksum);\n")
          .append("      byte[] actualChecksum = sha256(input, messageStartPosition, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      if (!java.util.Arrays.equals(expectedChecksum, actualChecksum)) {\n")
          .append(
              "        throw new IllegalArgumentException(\"Checksum mismatch for sha256 range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("      }\n");
    } else {
      throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm);
    }
    builder.append("    }\n");
  }

  /**
   * Appends conditional-block decode statements.
   *
   * @param builder destination source builder
   * @param resolvedIfBlock if-block definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendDecodeIfBlock(
      StringBuilder builder,
      ResolvedIfBlock resolvedIfBlock,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    String conditionExpression = ifConditionExpression(resolvedIfBlock.condition(), "value.");
    builder.append("    if (").append(conditionExpression).append(") {\n");
    appendDecodeMembers(
        builder, resolvedIfBlock.members(), messageType, generationContext, primitiveFieldByName);
    builder.append("    }\n");
  }
}
