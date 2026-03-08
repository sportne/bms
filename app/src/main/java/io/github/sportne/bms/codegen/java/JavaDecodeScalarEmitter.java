package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.byteOrderExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.decimalLiteral;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.primitiveTypeForBitFieldSize;

import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;

/** Emits Java decode statements for bitfields and numeric scalar members. */
final class JavaDecodeScalarEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaDecodeScalarEmitter() {}

  /**
   * Appends bitField decode statements.
   *
   * @param context shared decode emission context
   * @param resolvedBitField bitField member to decode
   */
  static void appendDecodeBitField(JavaDecodeContext context, ResolvedBitField resolvedBitField) {
    JavaDecodeFieldEmitter.appendPrimitiveDecode(
        context.builder(),
        context.ownerPrefix() + resolvedBitField.name(),
        primitiveTypeForBitFieldSize(resolvedBitField.size()),
        resolvedBitField.endian());
  }

  /**
   * Appends float decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedFloat float definition
   */
  static void appendDecodeFloat(
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
  static void appendDecodeScaledInt(
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
}
