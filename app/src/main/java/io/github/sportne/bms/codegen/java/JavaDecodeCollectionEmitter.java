package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.charsetExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.countExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.javaElementTypeForCollection;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.javaTypeForTypeRef;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.parseNumericLiteral;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.primitiveDecodeExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.terminatorComparisonExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.terminatorLiteral;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.toLoopIndexName;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.toLoopItemName;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.toPascalCase;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.wrapperTypeForPrimitive;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import java.math.BigInteger;

/** Emits Java decode statements for collection-like members. */
final class JavaDecodeCollectionEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaDecodeCollectionEmitter() {}

  /**
   * Appends fixed-array decode statements.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedArray array definition
   * @param fieldName field/member name for local variables
   */
  static void appendDecodeArray(
      JavaDecodeContext context,
      String targetExpression,
      ResolvedArray resolvedArray,
      String fieldName) {
    StringBuilder builder = context.builder();
    String loopIndexName = toLoopIndexName(fieldName);
    String loopItemName = toLoopItemName(fieldName);
    String elementType =
        javaElementTypeForCollection(resolvedArray.elementTypeRef(), context.generationContext());

    builder
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(resolvedArray.length())
        .append("];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n");

    appendDecodeCollectionElement(
        context, loopItemName, resolvedArray.elementTypeRef(), resolvedArray.endian());

    builder
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(loopItemName)
        .append(";\n")
        .append("    }\n");
  }

  /**
   * Appends vector decode statements.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   */
  static void appendDecodeVector(
      JavaDecodeContext context,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName) {
    if (resolvedVector.lengthMode() instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      appendDecodeCountedVector(
          context, targetExpression, resolvedVector, fieldName, resolvedCountFieldLength);
      return;
    }

    appendDecodeTerminatedVector(
        context,
        targetExpression,
        resolvedVector,
        fieldName,
        terminatorLiteral(resolvedVector.lengthMode()));
  }

  /**
   * Appends decode statements for count-based vectors.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   * @param resolvedCountFieldLength count-field length mode
   */
  private static void appendDecodeCountedVector(
      JavaDecodeContext context,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      ResolvedCountFieldLength resolvedCountFieldLength) {
    StringBuilder builder = context.builder();
    String countLocalName = "expected" + toPascalCase(fieldName) + "Count";
    PrimitiveType countFieldType =
        context.primitiveFieldByName().get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(context.ownerPrefix() + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String elementType =
        javaElementTypeForCollection(resolvedVector.elementTypeRef(), context.generationContext());
    String loopIndexName = toLoopIndexName(fieldName);
    String loopItemName = toLoopItemName(fieldName);

    builder
        .append("    int ")
        .append(countLocalName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(countLocalName)
        .append("];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(countLocalName)
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n");

    appendDecodeCollectionElement(
        context, loopItemName, resolvedVector.elementTypeRef(), resolvedVector.endian());

    builder
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(loopItemName)
        .append(";\n")
        .append("    }\n");
  }

  /**
   * Appends decode statements for terminator-based vectors.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   * @param literal terminator literal text
   */
  private static void appendDecodeTerminatedVector(
      JavaDecodeContext context,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      String literal) {
    StringBuilder builder = context.builder();
    PrimitiveType primitiveType =
        ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
    BigInteger numericLiteral = parseNumericLiteral(literal);

    String listName = toLoopItemName(fieldName) + "List";
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);
    String wrapperType = wrapperTypeForPrimitive(primitiveType);
    String elementType =
        javaElementTypeForCollection(resolvedVector.elementTypeRef(), context.generationContext());

    builder
        .append("    ArrayList<")
        .append(wrapperType)
        .append("> ")
        .append(listName)
        .append(" = new ArrayList<>();\n")
        .append("    while (true) {\n");

    appendDecodeCollectionElement(
        context, loopItemName, resolvedVector.elementTypeRef(), resolvedVector.endian());

    builder
        .append("      if (")
        .append(terminatorComparisonExpression(loopItemName, primitiveType, numericLiteral))
        .append(") {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(listName)
        .append(".add(")
        .append(loopItemName)
        .append(");\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(listName)
        .append(".size()];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(listName)
        .append(".size(); ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(listName)
        .append(".get(")
        .append(loopIndexName)
        .append(");\n")
        .append("    }\n");
  }

  /**
   * Appends one collection element decode statement.
   *
   * @param context shared decode emission context
   * @param targetLocalName local variable name that receives decoded element value
   * @param elementTypeRef collection element type reference
   * @param endian optional endian override from the collection definition
   */
  static void appendDecodeCollectionElement(
      JavaDecodeContext context,
      String targetLocalName,
      ResolvedTypeRef elementTypeRef,
      Endian endian) {
    StringBuilder builder = context.builder();
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      builder
          .append("      ")
          .append(primitiveTypeRef.primitiveType().javaTypeName())
          .append(' ')
          .append(targetLocalName)
          .append(" = ")
          .append(primitiveDecodeExpression(primitiveTypeRef.primitiveType(), endian))
          .append(";\n");
      return;
    }

    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, context.generationContext());
      builder
          .append("      ")
          .append(javaType)
          .append(' ')
          .append(targetLocalName)
          .append(" = ")
          .append(javaType)
          .append(".decode(input);\n");
      return;
    }

    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      builder.append("      double ").append(targetLocalName).append(";\n");
      JavaDecodeScalarEmitter.appendDecodeFloat(
          builder,
          targetLocalName,
          context.generationContext().reusableFloatByName().get(floatTypeRef.floatTypeName()));
      return;
    }

    ResolvedScaledInt resolvedScaledInt =
        context
            .generationContext()
            .reusableScaledIntByName()
            .get(((ScaledIntTypeRef) elementTypeRef).scaledIntTypeName());
    builder.append("      double ").append(targetLocalName).append(";\n");
    JavaDecodeScalarEmitter.appendDecodeScaledInt(builder, targetLocalName, resolvedScaledInt);
  }

  /**
   * Appends blobArray decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedBlobArray blobArray definition
   */
  static void appendDecodeBlobArray(
      StringBuilder builder, String targetExpression, ResolvedBlobArray resolvedBlobArray) {
    builder
        .append("    ")
        .append(targetExpression)
        .append(" = new byte[")
        .append(resolvedBlobArray.length())
        .append("];\n")
        .append("    input.get(")
        .append(targetExpression)
        .append(");\n");
  }

  /**
   * Appends blobVector decode statements.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedBlobVector blobVector definition
   * @param fieldName field/member name for local variables
   */
  static void appendDecodeBlobVector(
      JavaDecodeContext context,
      String targetExpression,
      ResolvedBlobVector resolvedBlobVector,
      String fieldName) {
    StringBuilder builder = context.builder();
    if (resolvedBlobVector.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      String countLocalName = "expected" + toPascalCase(fieldName) + "Count";
      PrimitiveType countFieldType =
          context.primitiveFieldByName().get(resolvedCountFieldLength.ref());
      String countExpression =
          countExpression(context.ownerPrefix() + resolvedCountFieldLength.ref(), countFieldType);
      String countMethod =
          countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";

      builder
          .append("    int ")
          .append(countLocalName)
          .append(" = ")
          .append(countMethod)
          .append('(')
          .append(countExpression)
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    ")
          .append(targetExpression)
          .append(" = new byte[")
          .append(countLocalName)
          .append("];\n")
          .append("    input.get(")
          .append(targetExpression)
          .append(");\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedBlobVector.lengthMode()));
    String tempName = toLoopItemName(fieldName) + "Buffer";
    builder
        .append("    ByteArrayOutputStream ")
        .append(tempName)
        .append(" = new ByteArrayOutputStream();\n")
        .append("    while (true) {\n")
        .append("      short nextByte = readUInt8(input);\n")
        .append("      if ((nextByte & 0xFFL) == ")
        .append(numericLiteral)
        .append("L) {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(tempName)
        .append(".write(nextByte & 0xFF);\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = ")
        .append(tempName)
        .append(".toByteArray();\n");
  }

  /**
   * Appends varString decode statements.
   *
   * @param context shared decode emission context
   * @param targetExpression assignment target expression
   * @param resolvedVarString varString definition
   * @param fieldName field/member name for local variables
   */
  static void appendDecodeVarString(
      JavaDecodeContext context,
      String targetExpression,
      ResolvedVarString resolvedVarString,
      String fieldName) {
    StringBuilder builder = context.builder();
    String charsetExpression = charsetExpression(resolvedVarString.encoding());
    if (resolvedVarString.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      String countLocalName = "expected" + toPascalCase(fieldName) + "Length";
      String bytesLocalName = "bytes" + toPascalCase(fieldName);
      PrimitiveType countFieldType =
          context.primitiveFieldByName().get(resolvedCountFieldLength.ref());
      String countExpression =
          countExpression(context.ownerPrefix() + resolvedCountFieldLength.ref(), countFieldType);
      String countMethod =
          countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";

      builder
          .append("    int ")
          .append(countLocalName)
          .append(" = ")
          .append(countMethod)
          .append('(')
          .append(countExpression)
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    byte[] ")
          .append(bytesLocalName)
          .append(" = new byte[")
          .append(countLocalName)
          .append("];\n")
          .append("    input.get(")
          .append(bytesLocalName)
          .append(");\n")
          .append("    ")
          .append(targetExpression)
          .append(" = new String(")
          .append(bytesLocalName)
          .append(", ")
          .append(charsetExpression)
          .append(");\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedVarString.lengthMode()));
    String tempName = "bytes" + toPascalCase(fieldName) + "Buffer";
    builder
        .append("    ByteArrayOutputStream ")
        .append(tempName)
        .append(" = new ByteArrayOutputStream();\n")
        .append("    while (true) {\n")
        .append("      short nextByte = readUInt8(input);\n")
        .append("      if ((nextByte & 0xFFL) == ")
        .append(numericLiteral)
        .append("L) {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(tempName)
        .append(".write(nextByte & 0xFF);\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = ")
        .append(tempName)
        .append(".toString(")
        .append(charsetExpression)
        .append(");\n");
  }
}
