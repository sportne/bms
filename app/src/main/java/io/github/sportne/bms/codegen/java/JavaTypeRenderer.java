package io.github.sportne.bms.codegen.java;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;

/**
 * Converts resolved model types into Java declaration types for generated code.
 *
 * <p>This helper keeps type-rendering rules in one place so generator orchestration stays smaller.
 */
final class JavaTypeRenderer {
  /** Prevents instantiation of this static utility class. */
  private JavaTypeRenderer() {}

  /**
   * Resolves Java declaration type for one member.
   *
   * @param member member to inspect
   * @param generationContext reusable lookup maps
   * @return Java declaration type
   */
  static String javaTypeForMember(
      ResolvedMessageMember member, JavaCodeGenerator.GenerationContext generationContext) {
    if (member instanceof ResolvedField resolvedField) {
      return javaTypeForTypeRef(resolvedField.typeRef(), generationContext);
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      return javaTypeForBitFieldSize(resolvedBitField.size());
    }
    if (member instanceof ResolvedFloat || member instanceof ResolvedScaledInt) {
      return "double";
    }
    if (member instanceof ResolvedArray resolvedArray) {
      return javaElementTypeForCollection(resolvedArray.elementTypeRef(), generationContext) + "[]";
    }
    if (member instanceof ResolvedVector resolvedVector) {
      return javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext)
          + "[]";
    }
    if (member instanceof ResolvedBlobArray || member instanceof ResolvedBlobVector) {
      return "byte[]";
    }
    if (member instanceof ResolvedVarString) {
      return "String";
    }
    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }

  /**
   * Resolves Java declaration type for one type reference.
   *
   * @param typeRef type reference to inspect
   * @param generationContext reusable lookup maps
   * @return Java declaration type
   */
  static String javaTypeForTypeRef(
      ResolvedTypeRef typeRef, JavaCodeGenerator.GenerationContext generationContext) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().javaTypeName();
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      ResolvedMessageType referenced =
          generationContext.messageTypeByName().get(messageTypeRef.messageTypeName());
      return referenced == null ? messageTypeRef.messageTypeName() : referenced.name();
    }
    if (typeRef instanceof FloatTypeRef || typeRef instanceof ScaledIntTypeRef) {
      return "double";
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray == null) {
        throw new IllegalStateException("Missing reusable array: " + arrayTypeRef.arrayTypeName());
      }
      return javaElementTypeForCollection(resolvedArray.elementTypeRef(), generationContext) + "[]";
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector == null) {
        throw new IllegalStateException(
            "Missing reusable vector: " + vectorTypeRef.vectorTypeName());
      }
      return javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext)
          + "[]";
    }
    if (typeRef instanceof BlobArrayTypeRef || typeRef instanceof BlobVectorTypeRef) {
      return "byte[]";
    }
    if (typeRef instanceof VarStringTypeRef) {
      return "String";
    }
    throw new IllegalStateException(
        "Unsupported type reference: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Resolves Java element type for collection members.
   *
   * @param elementTypeRef collection element type reference
   * @param generationContext reusable lookup maps
   * @return Java type used for one collection element
   */
  static String javaElementTypeForCollection(
      ResolvedTypeRef elementTypeRef, JavaCodeGenerator.GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().javaTypeName();
    }
    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      ResolvedMessageType referenced =
          generationContext.messageTypeByName().get(messageTypeRef.messageTypeName());
      return referenced == null ? messageTypeRef.messageTypeName() : referenced.name();
    }
    if (elementTypeRef instanceof FloatTypeRef || elementTypeRef instanceof ScaledIntTypeRef) {
      return "double";
    }
    throw new IllegalStateException(
        "Unsupported collection element type: " + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Resolves Java declaration type for bitField storage size.
   *
   * @param bitFieldSize bitField size
   * @return Java type used to store the raw bit container
   */
  static String javaTypeForBitFieldSize(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> "short";
      case U16 -> "int";
      case U32 -> "long";
      case U64 -> "long";
    };
  }
}
