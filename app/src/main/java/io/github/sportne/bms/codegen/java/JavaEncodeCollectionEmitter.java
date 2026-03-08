package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.charsetExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.countExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.javaElementTypeForCollection;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.parseNumericLiteral;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.primitiveLiteralExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.toLoopIndexName;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.toLoopItemName;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.toPascalCase;

import io.github.sportne.bms.codegen.common.LengthModeRules;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import java.math.BigInteger;
import java.util.Map;

/** Emits Java encode statements for collection-like members. */
final class JavaEncodeCollectionEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaEncodeCollectionEmitter() {}

  /**
   * Appends fixed-array encode statements.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to the array value
   * @param resolvedArray array definition
   * @param fieldName field/member name for exception text
   */
  static void appendEncodeArray(
      JavaEncodeContext context,
      String valueExpression,
      ResolvedArray resolvedArray,
      String fieldName) {
    StringBuilder builder = context.builder();
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);

    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(resolvedArray.length())
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" must contain exactly ")
        .append(resolvedArray.length())
        .append(" elements.\");\n")
        .append("    }\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(
            javaElementTypeForCollection(
                resolvedArray.elementTypeRef(), context.generationContext()))
        .append(' ')
        .append(loopItemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndexName)
        .append("];\n");

    appendEncodeCollectionElement(
        context, loopItemName, resolvedArray.elementTypeRef(), resolvedArray.endian());

    builder.append("    }\n");
  }

  /**
   * Appends vector encode statements.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to the vector value
   * @param resolvedVector vector definition
   * @param fieldName field/member name for exception text
   */
  static void appendEncodeVector(
      JavaEncodeContext context,
      String valueExpression,
      ResolvedVector resolvedVector,
      String fieldName) {
    StringBuilder builder = context.builder();
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);

    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n");

    appendVectorCountValidation(
        builder,
        resolvedVector.lengthMode(),
        valueExpression,
        context.primitiveFieldByName(),
        context.ownerPrefix(),
        fieldName);

    builder
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(valueExpression)
        .append(".length; ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(
            javaElementTypeForCollection(
                resolvedVector.elementTypeRef(), context.generationContext()))
        .append(' ')
        .append(loopItemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndexName)
        .append("];\n");

    appendEncodeCollectionElement(
        context, loopItemName, resolvedVector.elementTypeRef(), resolvedVector.endian());

    builder.append("    }\n");

    appendTerminatorEncode(
        context,
        resolvedVector.lengthMode(),
        resolvedVector.elementTypeRef(),
        resolvedVector.endian());
  }

  /**
   * Appends count-field validation for vector/blobVector encoders.
   *
   * @param builder destination source builder
   * @param lengthMode vector/blob length mode
   * @param valueExpression expression that resolves to vector/blob value
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   * @param fieldName field/member name for exception text
   */
  private static void appendVectorCountValidation(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      String valueExpression,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String fieldName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }

    PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String localName = "expected" + toPascalCase(fieldName) + "Count";

    builder
        .append("    int ")
        .append(localName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(localName)
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("    }\n");
  }

  /**
   * Appends terminator write statements for vector encoders.
   *
   * @param context shared encode emission context
   * @param lengthMode vector length mode
   * @param elementTypeRef vector element type
   * @param endian optional vector endian override
   */
  private static void appendTerminatorEncode(
      JavaEncodeContext context,
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      Endian endian) {
    String terminatorLiteral = terminatorLiteral(lengthMode);
    if (terminatorLiteral == null) {
      return;
    }

    BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
    PrimitiveType primitiveType = ((PrimitiveTypeRef) elementTypeRef).primitiveType();
    String terminatorExpression = primitiveLiteralExpression(primitiveType, numericLiteral);

    appendEncodeCollectionElement(context, terminatorExpression, elementTypeRef, endian);
  }

  /**
   * Appends one collection element encode statement.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to one element value
   * @param elementTypeRef collection element type reference
   * @param endian optional endian override from the collection definition
   */
  static void appendEncodeCollectionElement(
      JavaEncodeContext context,
      String valueExpression,
      ResolvedTypeRef elementTypeRef,
      Endian endian) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      JavaEncodeFieldEmitter.appendPrimitiveEncode(
          context.builder(), valueExpression, primitiveTypeRef.primitiveType(), endian);
      return;
    }

    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType =
          JavaEmitterSupport.javaTypeForTypeRef(messageTypeRef, context.generationContext());
      JavaEncodeFieldEmitter.appendMessageEncode(
          context.builder(), valueExpression, valueExpression, javaType);
      return;
    }

    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      JavaEncodeScalarEmitter.appendEncodeFloat(
          context.builder(),
          valueExpression,
          context.generationContext().reusableFloatByName().get(floatTypeRef.floatTypeName()),
          valueExpression);
      return;
    }

    ResolvedScaledInt resolvedScaledInt =
        context
            .generationContext()
            .reusableScaledIntByName()
            .get(((ScaledIntTypeRef) elementTypeRef).scaledIntTypeName());
    JavaEncodeScalarEmitter.appendEncodeScaledInt(
        context.builder(), valueExpression, resolvedScaledInt, valueExpression);
  }

  /**
   * Appends blobArray encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob value
   * @param resolvedBlobArray blobArray definition
   * @param fieldName field/member name for exception text
   */
  static void appendEncodeBlobArray(
      StringBuilder builder,
      String valueExpression,
      ResolvedBlobArray resolvedBlobArray,
      String fieldName) {
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(resolvedBlobArray.length())
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" must contain exactly ")
        .append(resolvedBlobArray.length())
        .append(" bytes.\");\n")
        .append("    }\n")
        .append("    out.write(")
        .append(valueExpression)
        .append(", 0, ")
        .append(resolvedBlobArray.length())
        .append(");\n");
  }

  /**
   * Appends blobVector encode statements.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to the blob value
   * @param resolvedBlobVector blobVector definition
   * @param fieldName field/member name for exception text
   */
  static void appendEncodeBlobVector(
      JavaEncodeContext context,
      String valueExpression,
      ResolvedBlobVector resolvedBlobVector,
      String fieldName) {
    StringBuilder builder = context.builder();
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n");

    appendVectorCountValidation(
        builder,
        resolvedBlobVector.lengthMode(),
        valueExpression,
        context.primitiveFieldByName(),
        context.ownerPrefix(),
        fieldName);

    builder
        .append("    out.write(")
        .append(valueExpression)
        .append(", 0, ")
        .append(valueExpression)
        .append(".length);\n");

    String terminatorLiteral = terminatorLiteral(resolvedBlobVector.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      builder.append("    writeUInt8(out, (short) ").append(numericLiteral).append(");\n");
    }
  }

  /**
   * Appends varString encode statements.
   *
   * @param context shared encode emission context
   * @param valueExpression expression that resolves to the string value
   * @param resolvedVarString varString definition
   * @param fieldName field/member name for exception text
   */
  static void appendEncodeVarString(
      JavaEncodeContext context,
      String valueExpression,
      ResolvedVarString resolvedVarString,
      String fieldName) {
    StringBuilder builder = context.builder();
    String encodedLocalName = "encoded" + toPascalCase(fieldName);
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    byte[] ")
        .append(encodedLocalName)
        .append(" = ")
        .append(valueExpression)
        .append(".getBytes(")
        .append(charsetExpression(resolvedVarString.encoding()))
        .append(");\n");

    appendVarStringCountValidation(
        builder,
        resolvedVarString.lengthMode(),
        encodedLocalName,
        context.primitiveFieldByName(),
        context.ownerPrefix(),
        fieldName);

    builder
        .append("    out.write(")
        .append(encodedLocalName)
        .append(", 0, ")
        .append(encodedLocalName)
        .append(".length);\n");

    String terminatorLiteral = terminatorLiteral(resolvedVarString.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      builder.append("    writeUInt8(out, (short) ").append(numericLiteral).append(");\n");
    }
  }

  /**
   * Appends count-field validation for varString encoders.
   *
   * @param builder destination source builder
   * @param lengthMode varString length mode
   * @param encodedBytesExpression expression that resolves to encoded bytes
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   * @param fieldName field/member name for exception text
   */
  private static void appendVarStringCountValidation(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      String encodedBytesExpression,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String fieldName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }

    PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String localName = "expected" + toPascalCase(fieldName) + "Length";

    builder
        .append("    int ")
        .append(localName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    if (")
        .append(encodedBytesExpression)
        .append(".length != ")
        .append(localName)
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" byte length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("    }\n");
  }

  /**
   * Resolves optional terminator literal from a length mode.
   *
   * @param lengthMode vector/blob/varString length mode
   * @return terminator literal when present, otherwise {@code null}
   */
  private static String terminatorLiteral(ResolvedLengthMode lengthMode) {
    if (lengthMode instanceof ResolvedTerminatorValueLength
        || lengthMode instanceof ResolvedTerminatorField) {
      return LengthModeRules.terminatorLiteral(lengthMode);
    }
    return null;
  }
}
