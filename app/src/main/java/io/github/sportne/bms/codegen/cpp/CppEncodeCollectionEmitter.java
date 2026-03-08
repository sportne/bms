package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.parseNumericLiteral;
import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.primitiveLiteralExpression;
import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.terminatorLiteral;
import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.toLoopIndexName;
import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.toLoopItemName;
import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.toPascalCase;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import java.math.BigInteger;

/** Emits C++ encode statements for collection-like members. */
final class CppEncodeCollectionEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppEncodeCollectionEmitter() {}

  /**
   * Appends fixed-array encode statements.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to the array value
   * @param resolvedArray array definition
   */
  static void appendEncodeArray(
      CppEncodeContext context, String valueExpression, ResolvedArray resolvedArray) {
    String loopIndex = toLoopIndexName(resolvedArray.name());
    String itemName = toLoopItemName(resolvedArray.name());
    context
        .builder()
        .append("  for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndex)
        .append("++) {\n")
        .append("    auto ")
        .append(itemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndex)
        .append("];\n");
    appendEncodeCollectionElement(
        context,
        itemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        resolvedArray.name());
    context.builder().append("  }\n");
  }

  /**
   * Appends vector encode statements.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to the vector value
   * @param resolvedVector vector definition
   * @param ownerName owner name used in helper labels
   */
  static void appendEncodeVector(
      CppEncodeContext context,
      String valueExpression,
      ResolvedVector resolvedVector,
      String ownerName) {
    appendCountValidation(context, resolvedVector.lengthMode(), valueExpression, ownerName);
    String loopIndex = toLoopIndexName(ownerName);
    String itemName = toLoopItemName(ownerName);
    context
        .builder()
        .append("  for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < ")
        .append(valueExpression)
        .append(".size(); ")
        .append(loopIndex)
        .append("++) {\n")
        .append("    auto ")
        .append(itemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndex)
        .append("];\n");
    appendEncodeCollectionElement(
        context, itemName, resolvedVector.elementTypeRef(), resolvedVector.endian(), ownerName);
    context.builder().append("  }\n");

    String terminatorLiteral = terminatorLiteral(resolvedVector.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      PrimitiveType primitiveType =
          ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
      CppEncodeFieldEmitter.appendEncodePrimitive(
          context.builder(),
          primitiveLiteralExpression(primitiveType, numericLiteral),
          primitiveType,
          resolvedVector.endian(),
          ownerName + "_terminator");
    }
  }

  /**
   * Appends blob-array encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob-array value
   */
  static void appendEncodeBlobArray(StringBuilder builder, String valueExpression) {
    builder
        .append("  out.insert(out.end(), ")
        .append(valueExpression)
        .append(".begin(), ")
        .append(valueExpression)
        .append(".end());\n");
  }

  /**
   * Appends blob-vector encode statements.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to the blob-vector value
   * @param resolvedBlobVector blob-vector definition
   * @param ownerName owner name used in helper labels
   */
  static void appendEncodeBlobVector(
      CppEncodeContext context,
      String valueExpression,
      ResolvedBlobVector resolvedBlobVector,
      String ownerName) {
    appendCountValidation(context, resolvedBlobVector.lengthMode(), valueExpression, ownerName);
    context
        .builder()
        .append("  out.insert(out.end(), ")
        .append(valueExpression)
        .append(".begin(), ")
        .append(valueExpression)
        .append(".end());\n");
    String terminatorLiteral = terminatorLiteral(resolvedBlobVector.lengthMode());
    if (terminatorLiteral != null) {
      CppEncodeFieldEmitter.appendEncodePrimitive(
          context.builder(),
          primitiveLiteralExpression(PrimitiveType.UINT8, parseNumericLiteral(terminatorLiteral)),
          PrimitiveType.UINT8,
          Endian.BIG,
          ownerName + "_terminator");
    }
  }

  /**
   * Appends encode statements for one collection element.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to one element value
   * @param elementTypeRef collection element type
   * @param endian optional endian override
   * @param ownerName owner name used in helper labels
   */
  private static void appendEncodeCollectionElement(
      CppEncodeContext context,
      String valueExpression,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      String ownerName) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      CppEncodeFieldEmitter.appendEncodePrimitive(
          context.builder(),
          valueExpression,
          primitiveTypeRef.primitiveType(),
          endian,
          ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof MessageTypeRef) {
      context
          .builder()
          .append("    {\n")
          .append("      std::vector<std::uint8_t> nested = ")
          .append(valueExpression)
          .append(".encode();\n")
          .append("      out.insert(out.end(), nested.begin(), nested.end());\n")
          .append("    }\n");
      return;
    }
    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      CppEncodeScalarEmitter.appendEncodeFloat(
          context.builder(),
          valueExpression,
          context.generationContext().reusableFloatByName().get(floatTypeRef.floatTypeName()),
          ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      CppEncodeScalarEmitter.appendEncodeScaledInt(
          context.builder(),
          valueExpression,
          context
              .generationContext()
              .reusableScaledIntByName()
              .get(scaledIntTypeRef.scaledIntTypeName()),
          ownerName + "_item");
      return;
    }
    throw new IllegalStateException(
        "Unsupported collection element type in C++ encode: "
            + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Appends count-field validation for vector/blob-vector encode paths.
   *
   * @param context shared encode emission context
   * @param lengthMode vector/blob length mode
   * @param valueExpression expression that resolves to collection value
   * @param ownerName owner name used in helper labels
   */
  private static void appendCountValidation(
      CppEncodeContext context,
      ResolvedLengthMode lengthMode,
      String valueExpression,
      String ownerName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }
    PrimitiveType countType = context.primitiveFieldByName().get(resolvedCountFieldLength.ref());
    if (countType == null) {
      return;
    }
    String countExpression = context.ownerPrefix() + resolvedCountFieldLength.ref();
    context
        .builder()
        .append("  std::size_t expected")
        .append(toPascalCase(ownerName))
        .append("Count = requireCount(")
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("  if (")
        .append(valueExpression)
        .append(".size() != expected")
        .append(toPascalCase(ownerName))
        .append("Count) {\n")
        .append("    throw std::invalid_argument(\"")
        .append(ownerName)
        .append(" length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("  }\n");
  }

  /**
   * Appends varString encode statements.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to the string value
   * @param resolvedVarString varString definition
   * @param fieldName field/member name used in helper labels
   */
  static void appendEncodeVarString(
      CppEncodeContext context,
      String valueExpression,
      ResolvedVarString resolvedVarString,
      String fieldName) {
    String encodedLocalName = "encoded" + toPascalCase(fieldName);
    context
        .builder()
        .append("  {\n")
        .append("    std::string ")
        .append(encodedLocalName)
        .append(" = ")
        .append(valueExpression)
        .append(";\n");
    appendVarStringCountValidation(
        context, resolvedVarString.lengthMode(), encodedLocalName, fieldName);
    context
        .builder()
        .append("    out.insert(out.end(), ")
        .append(encodedLocalName)
        .append(".begin(), ")
        .append(encodedLocalName)
        .append(".end());\n");
    String terminatorLiteral = terminatorLiteral(resolvedVarString.lengthMode());
    if (terminatorLiteral != null) {
      CppEncodeFieldEmitter.appendEncodePrimitive(
          context.builder(),
          primitiveLiteralExpression(PrimitiveType.UINT8, parseNumericLiteral(terminatorLiteral)),
          PrimitiveType.UINT8,
          Endian.BIG,
          fieldName + "_terminator");
    }
    context.builder().append("  }\n");
  }

  /**
   * Appends count-field validation for varString encode paths.
   *
   * @param context shared encode emission context
   * @param lengthMode varString length mode
   * @param encodedBytesExpression expression that resolves to the encoded byte string
   * @param fieldName field/member name used in helper labels
   */
  private static void appendVarStringCountValidation(
      CppEncodeContext context,
      ResolvedLengthMode lengthMode,
      String encodedBytesExpression,
      String fieldName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }
    PrimitiveType countType = context.primitiveFieldByName().get(resolvedCountFieldLength.ref());
    if (countType == null) {
      return;
    }
    context
        .builder()
        .append("    std::size_t expected")
        .append(toPascalCase(fieldName))
        .append("Length = requireCount(")
        .append(context.ownerPrefix())
        .append(resolvedCountFieldLength.ref())
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    if (")
        .append(encodedBytesExpression)
        .append(".size() != expected")
        .append(toPascalCase(fieldName))
        .append("Length) {\n")
        .append("      throw std::invalid_argument(\"")
        .append(fieldName)
        .append(" byte length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("    }\n");
  }
}
