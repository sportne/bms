package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.byteOrderExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.javaTypeForTypeRef;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.toPascalCase;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
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

/** Emits Java encode statements for field members and field type references. */
final class JavaEncodeFieldEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaEncodeFieldEmitter() {}

  /**
   * Appends encode statements for one field member.
   *
   * @param context shared encode emission context
   * @param resolvedField field member to encode
   */
  static void appendEncodeField(JavaEncodeContext context, ResolvedField resolvedField) {
    StringBuilder builder = context.builder();
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    String fieldExpression = context.ownerPrefix() + resolvedField.name();

    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendPrimitiveEncode(
          builder, fieldExpression, primitiveTypeRef.primitiveType(), resolvedField.endian());
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, context.generationContext());
      appendMessageEncode(builder, fieldExpression, resolvedField.name(), javaType);
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          context.generationContext().reusableFloatByName().get(floatTypeRef.floatTypeName());
      JavaEncodeScalarEmitter.appendEncodeFloat(
          builder, fieldExpression, resolvedFloat, resolvedField.name());
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          context
              .generationContext()
              .reusableScaledIntByName()
              .get(scaledIntTypeRef.scaledIntTypeName());
      JavaEncodeScalarEmitter.appendEncodeScaledInt(
          builder, fieldExpression, resolvedScaledInt, resolvedField.name());
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          context.generationContext().reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      JavaEncodeCollectionEmitter.appendEncodeArray(
          context, fieldExpression, resolvedArray, resolvedField.name());
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          context.generationContext().reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      JavaEncodeCollectionEmitter.appendEncodeVector(
          context, fieldExpression, resolvedVector, resolvedField.name());
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      ResolvedBlobArray resolvedBlobArray =
          context
              .generationContext()
              .reusableBlobArrayByName()
              .get(blobArrayTypeRef.blobArrayTypeName());
      JavaEncodeCollectionEmitter.appendEncodeBlobArray(
          context.builder(), fieldExpression, resolvedBlobArray, resolvedField.name());
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          context
              .generationContext()
              .reusableBlobVectorByName()
              .get(blobVectorTypeRef.blobVectorTypeName());
      JavaEncodeCollectionEmitter.appendEncodeBlobVector(
          context, fieldExpression, resolvedBlobVector, resolvedField.name());
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          context
              .generationContext()
              .reusableVarStringByName()
              .get(varStringTypeRef.varStringTypeName());
      JavaEncodeCollectionEmitter.appendEncodeVarString(
          context, fieldExpression, resolvedVarString, resolvedField.name());
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
  static void appendPrimitiveEncode(
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
  static void appendMessageEncode(
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
}
