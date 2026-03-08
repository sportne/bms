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
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import java.math.BigInteger;
import java.util.Map;

/** Emits C++ decode statements for collection-like members. */
final class CppDecodeCollectionEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppDecodeCollectionEmitter() {}

  /**
   * Appends fixed-array decode statements.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedArray array definition
   */
  static void appendDecodeArray(
      CppDecodeContext context, String targetExpression, ResolvedArray resolvedArray) {
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
        .append("++) {\n");
    appendDecodeCollectionElement(
        context,
        itemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        resolvedArray.name());
    context
        .builder()
        .append("    ")
        .append(targetExpression)
        .append('[')
        .append(loopIndex)
        .append("] = ")
        .append(itemName)
        .append(";\n")
        .append("  }\n");
  }

  /**
   * Appends vector decode statements.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param ownerName owner name used in helper labels
   */
  static void appendDecodeVector(
      CppDecodeContext context,
      String targetExpression,
      ResolvedVector resolvedVector,
      String ownerName) {
    if (resolvedVector.lengthMode() instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      appendDecodeCountedVector(
          context, targetExpression, resolvedVector, ownerName, resolvedCountFieldLength);
      return;
    }
    appendDecodeTerminatedVector(context, targetExpression, resolvedVector, ownerName);
  }

  /**
   * Appends decode statements for count-based vectors.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param ownerName owner name used in helper labels
   * @param resolvedCountFieldLength count-field length mode
   */
  private static void appendDecodeCountedVector(
      CppDecodeContext context,
      String targetExpression,
      ResolvedVector resolvedVector,
      String ownerName,
      ResolvedCountFieldLength resolvedCountFieldLength) {
    PrimitiveType countType = context.primitiveFieldByName().get(resolvedCountFieldLength.ref());
    if (countType == null) {
      return;
    }
    String itemName = toLoopItemName(ownerName);
    String loopIndex = toLoopIndexName(ownerName);
    context
        .builder()
        .append("  {\n")
        .append("    std::size_t expected")
        .append(toPascalCase(ownerName))
        .append("Count = requireCount(")
        .append(context.ownerPrefix())
        .append(resolvedCountFieldLength.ref())
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("    ")
        .append(targetExpression)
        .append(".reserve(expected")
        .append(toPascalCase(ownerName))
        .append("Count);\n")
        .append("    for (std::size_t ")
        .append(loopIndex)
        .append(" = 0; ")
        .append(loopIndex)
        .append(" < expected")
        .append(toPascalCase(ownerName))
        .append("Count; ")
        .append(loopIndex)
        .append("++) {\n");
    appendDecodeCollectionElement(
        context, itemName, resolvedVector.elementTypeRef(), resolvedVector.endian(), ownerName);
    context
        .builder()
        .append("      ")
        .append(targetExpression)
        .append(".push_back(")
        .append(itemName)
        .append(");\n")
        .append("    }\n")
        .append("  }\n");
  }

  /**
   * Appends decode statements for terminator-based vectors.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeTerminatedVector(
      CppDecodeContext context,
      String targetExpression,
      ResolvedVector resolvedVector,
      String ownerName) {
    String literal = terminatorLiteral(resolvedVector.lengthMode());
    BigInteger numericLiteral = parseNumericLiteral(literal);
    PrimitiveType primitiveType =
        ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
    String itemName = toLoopItemName(ownerName);

    context
        .builder()
        .append("  ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("  while (true) {\n");
    appendDecodeCollectionElement(
        new CppDecodeContext(
            context.builder(),
            context.messageType(),
            context.generationContext(),
            Map.of(),
            "value."),
        itemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        ownerName);
    context
        .builder()
        .append("    if (")
        .append(itemName)
        .append(" == ")
        .append(primitiveLiteralExpression(primitiveType, numericLiteral))
        .append(") {\n")
        .append("      break;\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(".push_back(")
        .append(itemName)
        .append(");\n")
        .append("  }\n");
  }

  /**
   * Appends decode statements for one collection element.
   *
   * @param context shared decode emission context
   * @param localName local variable that receives decoded value
   * @param elementTypeRef collection element type
   * @param endian optional endian override
   * @param ownerName owner name used in helper labels
   */
  private static void appendDecodeCollectionElement(
      CppDecodeContext context,
      String localName,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      String ownerName) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      context
          .builder()
          .append("    ")
          .append(primitiveTypeRef.primitiveType().cppTypeName())
          .append(' ')
          .append(localName)
          .append(" = readIntegral<")
          .append(primitiveTypeRef.primitiveType().cppTypeName())
          .append(">(data, cursor, ")
          .append(endian == Endian.LITTLE ? "true" : "false")
          .append(", \"")
          .append(ownerName)
          .append("_item\");\n");
      return;
    }
    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String cppType =
          CppEmitterSupport.cppMessageTypeName(
              messageTypeRef.messageTypeName(),
              context.messageType().effectiveNamespace(),
              context.generationContext());
      context
          .builder()
          .append("    ")
          .append(cppType)
          .append(' ')
          .append(localName)
          .append(" = ")
          .append(cppType)
          .append("::decodeFrom(data, cursor);\n");
      return;
    }
    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          context.generationContext().reusableFloatByName().get(floatTypeRef.floatTypeName());
      context.builder().append("    double ").append(localName).append("{};\n");
      CppDecodeScalarEmitter.appendDecodeFloat(
          context.builder(), localName, resolvedFloat, ownerName + "_item");
      return;
    }
    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          context
              .generationContext()
              .reusableScaledIntByName()
              .get(scaledIntTypeRef.scaledIntTypeName());
      context.builder().append("    double ").append(localName).append("{};\n");
      CppDecodeScalarEmitter.appendDecodeScaledInt(
          context.builder(), localName, resolvedScaledInt, ownerName + "_item");
      return;
    }
    throw new IllegalStateException(
        "Unsupported collection element type in C++ decode: "
            + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Appends blob-array decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param fieldName field name used in helper labels
   */
  static void appendDecodeBlobArray(
      StringBuilder builder, String targetExpression, String fieldName) {
    builder
        .append("  requireReadable(data, cursor, ")
        .append(targetExpression)
        .append(".size(), \"")
        .append(fieldName)
        .append("\");\n")
        .append("  std::copy_n(data.begin() + static_cast<std::ptrdiff_t>(cursor), ")
        .append(targetExpression)
        .append(".size(), ")
        .append(targetExpression)
        .append(".begin());\n")
        .append("  cursor += ")
        .append(targetExpression)
        .append(".size();\n");
  }

  /**
   * Appends blob-vector decode statements.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedBlobVector blob-vector definition
   * @param ownerName owner name used in helper labels
   */
  static void appendDecodeBlobVector(
      CppDecodeContext context,
      String targetExpression,
      ResolvedBlobVector resolvedBlobVector,
      String ownerName) {
    if (resolvedBlobVector.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      PrimitiveType countType = context.primitiveFieldByName().get(resolvedCountFieldLength.ref());
      if (countType == null) {
        return;
      }
      context
          .builder()
          .append("  {\n")
          .append("    std::size_t expected")
          .append(toPascalCase(ownerName))
          .append("Count = requireCount(")
          .append(context.ownerPrefix())
          .append(resolvedCountFieldLength.ref())
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    requireReadable(data, cursor, expected")
          .append(toPascalCase(ownerName))
          .append("Count, \"")
          .append(ownerName)
          .append("\");\n")
          .append("    ")
          .append(targetExpression)
          .append(".assign(\n")
          .append("        data.begin() + static_cast<std::ptrdiff_t>(cursor),\n")
          .append("        data.begin() + static_cast<std::ptrdiff_t>(cursor + expected")
          .append(toPascalCase(ownerName))
          .append("Count));\n")
          .append("    cursor += expected")
          .append(toPascalCase(ownerName))
          .append("Count;\n")
          .append("  }\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedBlobVector.lengthMode()));
    context
        .builder()
        .append("  ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("  while (true) {\n")
        .append("    std::uint8_t nextByte = readIntegral<std::uint8_t>(data, cursor, false, \"")
        .append(ownerName)
        .append("_item\");\n")
        .append("    if (nextByte == ")
        .append(primitiveLiteralExpression(PrimitiveType.UINT8, numericLiteral))
        .append(") {\n")
        .append("      break;\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(".push_back(nextByte);\n")
        .append("  }\n");
  }

  /**
   * Appends varString decode statements.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedVarString varString definition
   * @param fieldName field/member name used in helper labels
   */
  static void appendDecodeVarString(
      CppDecodeContext context,
      String targetExpression,
      ResolvedVarString resolvedVarString,
      String fieldName) {
    if (resolvedVarString.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      PrimitiveType countType = context.primitiveFieldByName().get(resolvedCountFieldLength.ref());
      if (countType == null) {
        return;
      }
      context
          .builder()
          .append("  std::size_t expected")
          .append(toPascalCase(fieldName))
          .append("Length = requireCount(")
          .append(context.ownerPrefix())
          .append(resolvedCountFieldLength.ref())
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("  requireReadable(data, cursor, expected")
          .append(toPascalCase(fieldName))
          .append("Length, \"")
          .append(fieldName)
          .append("\");\n")
          .append("  ")
          .append(targetExpression)
          .append(".assign(\n")
          .append("      reinterpret_cast<const char*>(data.data() + cursor),\n")
          .append("      expected")
          .append(toPascalCase(fieldName))
          .append("Length);\n")
          .append("  cursor += expected")
          .append(toPascalCase(fieldName))
          .append("Length;\n");
      return;
    }
    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedVarString.lengthMode()));
    context
        .builder()
        .append("  ")
        .append(targetExpression)
        .append(".clear();\n")
        .append("  while (true) {\n")
        .append("    std::uint8_t nextByte = readIntegral<std::uint8_t>(data, cursor, false, \"")
        .append(fieldName)
        .append("_item\");\n")
        .append("    if (nextByte == ")
        .append(primitiveLiteralExpression(PrimitiveType.UINT8, numericLiteral))
        .append(") {\n")
        .append("      break;\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(".push_back(static_cast<char>(nextByte));\n")
        .append("  }\n");
  }
}
