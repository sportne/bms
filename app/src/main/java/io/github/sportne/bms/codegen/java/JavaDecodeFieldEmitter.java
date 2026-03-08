package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.byteOrderExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.javaTypeForTypeRef;

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

/** Emits Java decode statements for field members and field type references. */
final class JavaDecodeFieldEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaDecodeFieldEmitter() {}

  /**
   * Appends decode statements for one field member.
   *
   * @param context shared decode emission context
   * @param resolvedField field member to decode
   */
  static void appendDecodeField(JavaDecodeContext context, ResolvedField resolvedField) {
    StringBuilder builder = context.builder();
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    String targetExpression = context.ownerPrefix() + resolvedField.name();

    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendPrimitiveDecode(
          builder, targetExpression, primitiveTypeRef.primitiveType(), resolvedField.endian());
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, context.generationContext());
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
          context.generationContext().reusableFloatByName().get(floatTypeRef.floatTypeName());
      JavaDecodeScalarEmitter.appendDecodeFloat(builder, targetExpression, resolvedFloat);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          context
              .generationContext()
              .reusableScaledIntByName()
              .get(scaledIntTypeRef.scaledIntTypeName());
      JavaDecodeScalarEmitter.appendDecodeScaledInt(builder, targetExpression, resolvedScaledInt);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          context.generationContext().reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      JavaDecodeCollectionEmitter.appendDecodeArray(
          context, targetExpression, resolvedArray, resolvedField.name());
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          context.generationContext().reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      JavaDecodeCollectionEmitter.appendDecodeVector(
          context, targetExpression, resolvedVector, resolvedField.name());
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      ResolvedBlobArray resolvedBlobArray =
          context
              .generationContext()
              .reusableBlobArrayByName()
              .get(blobArrayTypeRef.blobArrayTypeName());
      JavaDecodeCollectionEmitter.appendDecodeBlobArray(
          context.builder(), targetExpression, resolvedBlobArray);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          context
              .generationContext()
              .reusableBlobVectorByName()
              .get(blobVectorTypeRef.blobVectorTypeName());
      JavaDecodeCollectionEmitter.appendDecodeBlobVector(
          context, targetExpression, resolvedBlobVector, resolvedField.name());
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          context
              .generationContext()
              .reusableVarStringByName()
              .get(varStringTypeRef.varStringTypeName());
      JavaDecodeCollectionEmitter.appendDecodeVarString(
          context, targetExpression, resolvedVarString, resolvedField.name());
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
  static void appendPrimitiveDecode(
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
}
