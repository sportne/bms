package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.byteOrderExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.decimalLiteral;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;

/** Emits Java encode statements for bitfields and numeric scalar members. */
final class JavaEncodeScalarEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaEncodeScalarEmitter() {}

  /**
   * Appends bitField encode statements.
   *
   * @param context shared encode emission context
   * @param resolvedBitField bitField member to encode
   */
  static void appendEncodeBitField(JavaEncodeContext context, ResolvedBitField resolvedBitField) {
    PrimitiveType primitiveType = primitiveTypeForBitFieldSize(resolvedBitField.size());
    JavaEncodeFieldEmitter.appendPrimitiveEncode(
        context.builder(),
        context.ownerPrefix() + resolvedBitField.name(),
        primitiveType,
        resolvedBitField.endian());
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
  static void appendEncodeFloat(
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
  static void appendEncodeScaledInt(
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
}
