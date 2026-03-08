package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.bitFieldStoragePrimitive;
import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.decimalLiteral;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;

/** Emits C++ decode statements for bitfields and numeric scalar members. */
final class CppDecodeScalarEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppDecodeScalarEmitter() {}

  /**
   * Appends bitField decode statements.
   *
   * @param context shared decode emission context
   * @param resolvedBitField bitField member to decode
   */
  static void appendDecodeBitField(CppDecodeContext context, ResolvedBitField resolvedBitField) {
    CppDecodeFieldEmitter.appendDecodePrimitive(
        context.builder(),
        context.ownerPrefix() + resolvedBitField.name(),
        bitFieldStoragePrimitive(resolvedBitField.size()),
        resolvedBitField.endian(),
        resolvedBitField.name());
  }

  /**
   * Appends float decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedFloat float definition
   * @param fieldName field/member name used in helper labels
   */
  static void appendDecodeFloat(
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
   * @param fieldName field/member name used in helper labels
   */
  static void appendDecodeScaledInt(
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
}
