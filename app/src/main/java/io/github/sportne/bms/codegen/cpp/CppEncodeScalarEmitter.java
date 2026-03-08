package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.decimalLiteral;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;

/** Emits C++ encode statements for numeric scalar member kinds. */
final class CppEncodeScalarEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppEncodeScalarEmitter() {}

  /**
   * Appends float encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the logical float value
   * @param resolvedFloat float definition
   * @param fieldName field/member name used in exception text
   */
  static void appendEncodeFloat(
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
  static void appendEncodeScaledInt(
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
}
