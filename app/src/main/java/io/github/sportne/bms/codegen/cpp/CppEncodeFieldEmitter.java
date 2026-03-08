package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.bitFieldStoragePrimitive;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;

/** Emits C++ encode statements for field members and field type references. */
final class CppEncodeFieldEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppEncodeFieldEmitter() {}

  /**
   * Appends encode statements for one field member.
   *
   * @param context shared encode emission context
   * @param resolvedField field member to encode
   */
  static void appendEncodeField(CppEncodeContext context, ResolvedField resolvedField) {
    appendEncodeField(
        context,
        resolvedField.typeRef(),
        context.ownerPrefix() + resolvedField.name(),
        resolvedField.name(),
        resolvedField.endian());
  }

  /**
   * Appends encode statements for one bitField member.
   *
   * @param context shared encode emission context
   * @param resolvedBitField bitField member to encode
   */
  static void appendEncodeBitField(CppEncodeContext context, ResolvedBitField resolvedBitField) {
    appendEncodePrimitive(
        context.builder(),
        context.ownerPrefix() + resolvedBitField.name(),
        bitFieldStoragePrimitive(resolvedBitField.size()),
        resolvedBitField.endian(),
        resolvedBitField.name());
  }

  /**
   * Appends encode statements for one field (including reusable type references).
   *
   * @param context shared encode emission context
   * @param typeRef field type reference
   * @param valueExpression expression that resolves to the field value
   * @param fieldName field name used in helper labels
   * @param endian optional endian override
   */
  private static void appendEncodeField(
      CppEncodeContext context,
      ResolvedTypeRef typeRef,
      String valueExpression,
      String fieldName,
      Endian endian) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendEncodePrimitive(
          context.builder(), valueExpression, primitiveTypeRef.primitiveType(), endian, fieldName);
      return;
    }
    if (typeRef instanceof MessageTypeRef) {
      context
          .builder()
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
          context.generationContext().reusableFloatByName().get(floatTypeRef.floatTypeName());
      CppEncodeScalarEmitter.appendEncodeFloat(
          context.builder(), valueExpression, resolvedFloat, fieldName);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          context
              .generationContext()
              .reusableScaledIntByName()
              .get(scaledIntTypeRef.scaledIntTypeName());
      CppEncodeScalarEmitter.appendEncodeScaledInt(
          context.builder(), valueExpression, resolvedScaledInt, fieldName);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          context.generationContext().reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      CppEncodeCollectionEmitter.appendEncodeArray(context, valueExpression, resolvedArray);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          context.generationContext().reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      CppEncodeCollectionEmitter.appendEncodeVector(
          context, valueExpression, resolvedVector, fieldName);
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef) {
      CppEncodeCollectionEmitter.appendEncodeBlobArray(context.builder(), valueExpression);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          context
              .generationContext()
              .reusableBlobVectorByName()
              .get(blobVectorTypeRef.blobVectorTypeName());
      CppEncodeCollectionEmitter.appendEncodeBlobVector(
          context, valueExpression, resolvedBlobVector, fieldName);
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          context
              .generationContext()
              .reusableVarStringByName()
              .get(varStringTypeRef.varStringTypeName());
      CppEncodeCollectionEmitter.appendEncodeVarString(
          context, valueExpression, resolvedVarString, fieldName);
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
  static void appendEncodePrimitive(
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
}
