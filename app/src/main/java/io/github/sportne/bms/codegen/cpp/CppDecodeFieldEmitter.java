package io.github.sportne.bms.codegen.cpp;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
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

/** Emits C++ decode statements for field members and field type references. */
final class CppDecodeFieldEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppDecodeFieldEmitter() {}

  /**
   * Appends decode statements for one field member.
   *
   * @param context shared decode emission context
   * @param resolvedField field member to decode
   */
  static void appendDecodeField(CppDecodeContext context, ResolvedField resolvedField) {
    appendDecodeField(
        context,
        resolvedField.typeRef(),
        context.ownerPrefix() + resolvedField.name(),
        resolvedField.name(),
        resolvedField.endian());
  }

  /**
   * Appends decode statements for one field (including reusable type references).
   *
   * @param context shared decode emission context
   * @param typeRef field type reference
   * @param targetExpression assignment target expression
   * @param fieldName field name used in helper labels
   * @param endian optional endian override
   */
  private static void appendDecodeField(
      CppDecodeContext context,
      ResolvedTypeRef typeRef,
      String targetExpression,
      String fieldName,
      Endian endian) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendDecodePrimitive(
          context.builder(), targetExpression, primitiveTypeRef.primitiveType(), endian, fieldName);
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      String cppType =
          CppEmitterSupport.cppMessageTypeName(
              messageTypeRef.messageTypeName(),
              context.messageType().effectiveNamespace(),
              context.generationContext());
      context
          .builder()
          .append("  ")
          .append(targetExpression)
          .append(" = ")
          .append(cppType)
          .append("::decodeFrom(data, cursor);\n");
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          context.generationContext().reusableFloatByName().get(floatTypeRef.floatTypeName());
      CppDecodeScalarEmitter.appendDecodeFloat(
          context.builder(), targetExpression, resolvedFloat, fieldName);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          context
              .generationContext()
              .reusableScaledIntByName()
              .get(scaledIntTypeRef.scaledIntTypeName());
      CppDecodeScalarEmitter.appendDecodeScaledInt(
          context.builder(), targetExpression, resolvedScaledInt, fieldName);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          context.generationContext().reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      CppDecodeCollectionEmitter.appendDecodeArray(context, targetExpression, resolvedArray);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          context.generationContext().reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      CppDecodeCollectionEmitter.appendDecodeVector(
          context, targetExpression, resolvedVector, fieldName);
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef) {
      CppDecodeCollectionEmitter.appendDecodeBlobArray(
          context.builder(), targetExpression, fieldName);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          context
              .generationContext()
              .reusableBlobVectorByName()
              .get(blobVectorTypeRef.blobVectorTypeName());
      CppDecodeCollectionEmitter.appendDecodeBlobVector(
          context, targetExpression, resolvedBlobVector, fieldName);
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          context
              .generationContext()
              .reusableVarStringByName()
              .get(varStringTypeRef.varStringTypeName());
      CppDecodeCollectionEmitter.appendDecodeVarString(
          context, targetExpression, resolvedVarString, fieldName);
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
   * @param label label used in helper diagnostics
   */
  static void appendDecodePrimitive(
      StringBuilder builder,
      String targetExpression,
      PrimitiveType primitiveType,
      Endian endian,
      String label) {
    boolean littleEndian = endian == Endian.LITTLE;
    String littleEndianLiteral = littleEndian ? "true" : "false";
    builder
        .append("  ")
        .append(targetExpression)
        .append(" = readIntegral<")
        .append(primitiveType.cppTypeName())
        .append(">(data, cursor, ")
        .append(littleEndianLiteral)
        .append(", \"")
        .append(label)
        .append("\");\n");
  }
}
