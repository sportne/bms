package io.github.sportne.bms.codegen.cpp;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Converts resolved model types into C++ declaration types for generated code.
 *
 * <p>This helper centralizes C++ type rendering so generator orchestration remains smaller.
 */
final class CppTypeRenderer {
  /** Prevents instantiation of this static utility class. */
  private CppTypeRenderer() {}

  /**
   * Returns the C++ field type for one resolved message member.
   *
   * @param member member whose field type is needed
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @return C++ field type, or {@code null} when member has no field declaration
   */
  static String memberCppType(
      ResolvedMessageMember member,
      String currentNamespace,
      CppCodeGenerator.GenerationContext generationContext) {
    if (member instanceof ResolvedField resolvedField) {
      return toCppTypeForTypeRef(
          resolvedField.typeRef(), currentNamespace, generationContext, resolvedField.name());
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      return bitFieldStorageCppType(resolvedBitField.size());
    }
    if (member instanceof ResolvedFloat || member instanceof ResolvedScaledInt) {
      return "double";
    }
    if (member instanceof ResolvedArray resolvedArray) {
      return "std::array<"
          + collectionElementCppType(
              resolvedArray.elementTypeRef(),
              currentNamespace,
              generationContext,
              resolvedArray.name())
          + ", "
          + resolvedArray.length()
          + ">";
    }
    if (member instanceof ResolvedVector resolvedVector) {
      return "std::vector<"
          + collectionElementCppType(
              resolvedVector.elementTypeRef(),
              currentNamespace,
              generationContext,
              resolvedVector.name())
          + ">";
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      return "std::array<std::uint8_t, " + resolvedBlobArray.length() + ">";
    }
    if (member instanceof ResolvedBlobVector) {
      return "std::vector<std::uint8_t>";
    }
    if (member instanceof ResolvedVarString) {
      return "std::string";
    }
    return null;
  }

  /**
   * Resolves one field type reference into its generated C++ field type.
   *
   * @param typeRef resolved type reference
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @param fieldName field name used in fallback exceptions
   * @return C++ field type
   */
  static String toCppTypeForTypeRef(
      ResolvedTypeRef typeRef,
      String currentNamespace,
      CppCodeGenerator.GenerationContext generationContext,
      String fieldName) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().cppTypeName();
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      return cppMessageTypeName(
          messageTypeRef.messageTypeName(), currentNamespace, generationContext);
    }
    if (typeRef instanceof FloatTypeRef || typeRef instanceof ScaledIntTypeRef) {
      return "double";
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray == null) {
        throw new IllegalStateException("Missing reusable array for " + fieldName + '.');
      }
      return "std::array<"
          + collectionElementCppType(
              resolvedArray.elementTypeRef(), currentNamespace, generationContext, fieldName)
          + ", "
          + resolvedArray.length()
          + ">";
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector == null) {
        throw new IllegalStateException("Missing reusable vector for " + fieldName + '.');
      }
      return "std::vector<"
          + collectionElementCppType(
              resolvedVector.elementTypeRef(), currentNamespace, generationContext, fieldName)
          + ">";
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      ResolvedBlobArray resolvedBlobArray =
          generationContext.reusableBlobArrayByName().get(blobArrayTypeRef.blobArrayTypeName());
      if (resolvedBlobArray == null) {
        throw new IllegalStateException("Missing reusable blobArray for " + fieldName + '.');
      }
      return "std::array<std::uint8_t, " + resolvedBlobArray.length() + ">";
    }
    if (typeRef instanceof BlobVectorTypeRef) {
      return "std::vector<std::uint8_t>";
    }
    if (typeRef instanceof VarStringTypeRef) {
      return "std::string";
    }
    throw new IllegalStateException(
        "Unsupported type reference: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Resolves one collection element type into a C++ type.
   *
   * @param elementTypeRef resolved element type reference
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @param ownerName owner name used in fallback exceptions
   * @return C++ element type
   */
  static String collectionElementCppType(
      ResolvedTypeRef elementTypeRef,
      String currentNamespace,
      CppCodeGenerator.GenerationContext generationContext,
      String ownerName) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().cppTypeName();
    }
    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      return cppMessageTypeName(
          messageTypeRef.messageTypeName(), currentNamespace, generationContext);
    }
    if (elementTypeRef instanceof FloatTypeRef || elementTypeRef instanceof ScaledIntTypeRef) {
      return "double";
    }
    throw new IllegalStateException(
        "Unsupported collection element type for "
            + ownerName
            + ": "
            + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Resolves one message type reference into a local or fully-qualified C++ type.
   *
   * @param messageTypeName referenced message type name
   * @param currentNamespace namespace of the current generated message
   * @param generationContext reusable lookup maps
   * @return C++ type expression
   */
  static String cppMessageTypeName(
      String messageTypeName,
      String currentNamespace,
      CppCodeGenerator.GenerationContext generationContext) {
    ResolvedMessageType referenced = generationContext.messageTypeByName().get(messageTypeName);
    if (referenced == null) {
      return messageTypeName;
    }
    if (referenced.effectiveNamespace().equals(currentNamespace)) {
      return referenced.name();
    }
    return "::" + toCppNamespace(referenced.effectiveNamespace()) + "::" + referenced.name();
  }

  /**
   * Maps one bitfield storage size to the C++ field type used in generated structs.
   *
   * @param bitFieldSize bitfield storage size
   * @return C++ type name for the raw storage field
   */
  private static String bitFieldStorageCppType(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> "std::uint8_t";
      case U16 -> "std::uint16_t";
      case U32 -> "std::uint32_t";
      case U64 -> "std::uint64_t";
    };
  }

  /**
   * Converts a dot-delimited namespace to a C++ {@code ::}-delimited namespace.
   *
   * @param namespaceValue dot-delimited namespace
   * @return C++ namespace string
   */
  private static String toCppNamespace(String namespaceValue) {
    return String.join("::", splitNamespace(namespaceValue));
  }

  /**
   * Splits a dot-delimited namespace into non-blank segments.
   *
   * @param namespaceValue dot-delimited namespace
   * @return namespace segments in order
   */
  private static List<String> splitNamespace(String namespaceValue) {
    String[] rawSegments = namespaceValue.split("\\.");
    List<String> segments = new ArrayList<>();
    for (String segment : rawSegments) {
      if (!segment.isBlank()) {
        segments.add(segment);
      }
    }
    return segments;
  }
}
