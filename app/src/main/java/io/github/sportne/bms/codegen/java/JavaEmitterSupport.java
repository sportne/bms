package io.github.sportne.bms.codegen.java;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.codegen.common.LengthModeRules;
import io.github.sportne.bms.codegen.common.PrimitiveFieldIndex;
import io.github.sportne.bms.codegen.common.PrimitiveNumericRules;
import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.IfLogicalOperator;
import io.github.sportne.bms.model.StringEncoding;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedIfComparison;
import io.github.sportne.bms.model.resolved.ResolvedIfCondition;
import io.github.sportne.bms.model.resolved.ResolvedIfLogicalCondition;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * Shared helper methods used by Java encode/decode emitters.
 *
 * <p>Keeping these methods in one place avoids duplicating logic between the two emitter classes.
 */
final class JavaEmitterSupport {
  /** Prevents instantiation of this static utility class. */
  private JavaEmitterSupport() {}

  /**
   * Builds a map from primitive field name to primitive type for one message.
   *
   * @param messageType resolved message type
   * @return primitive field lookup map
   */
  static Map<String, PrimitiveType> primitiveFieldsByName(ResolvedMessageType messageType) {
    return PrimitiveFieldIndex.collect(messageType);
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
    return JavaTypeRenderer.javaTypeForTypeRef(typeRef, generationContext);
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
    return JavaTypeRenderer.javaElementTypeForCollection(elementTypeRef, generationContext);
  }

  /**
   * Resolves primitive storage type for one bit field size.
   *
   * @param bitFieldSize declared bit field storage size
   * @return primitive integer type used to encode/decode the bit field word
   */
  static PrimitiveType primitiveTypeForBitFieldSize(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> PrimitiveType.UINT8;
      case U16 -> PrimitiveType.UINT16;
      case U32 -> PrimitiveType.UINT32;
      case U64 -> PrimitiveType.UINT64;
    };
  }

  /**
   * Extracts one terminator literal from a terminator length mode.
   *
   * @param lengthMode length mode to inspect
   * @return literal value text
   */
  static String terminatorLiteral(ResolvedLengthMode lengthMode) {
    if (lengthMode == null) {
      throw new IllegalStateException("Terminator length mode expected but was null.");
    }
    return LengthModeRules.terminatorLiteral(lengthMode);
  }

  /**
   * Parses a checksum range and fails hard when the value is unexpectedly invalid.
   *
   * @param rangeText checksum range text from XML
   * @return parsed checksum range
   */
  static ChecksumRangeRules.ChecksumRange requiredChecksumRange(String rangeText) {
    return ChecksumRangeRules.require(rangeText);
  }

  /**
   * Parses one numeric literal used by terminator modes.
   *
   * @param literal raw literal text from XML
   * @return parsed integer value
   * @throws NumberFormatException when the literal is not numeric
   */
  static BigInteger parseNumericLiteral(String literal) {
    return PrimitiveNumericRules.parseNumericLiteral(literal);
  }

  /**
   * Converts an optional endian value to a Java {@link java.nio.ByteOrder} expression.
   *
   * @param endian optional endian override from the resolved model
   * @return Java source expression for the matching byte order
   */
  static String byteOrderExpression(Endian endian) {
    if (endian == Endian.LITTLE) {
      return "ByteOrder.LITTLE_ENDIAN";
    }
    return "ByteOrder.BIG_ENDIAN";
  }

  /**
   * Converts one resolved string encoding to a Java {@link java.nio.charset.Charset} expression.
   *
   * @param encoding resolved string encoding
   * @return Java source expression for the matching charset
   */
  static String charsetExpression(StringEncoding encoding) {
    if (encoding == StringEncoding.ASCII) {
      return "StandardCharsets.US_ASCII";
    }
    return "StandardCharsets.UTF_8";
  }

  /**
   * Converts an optional decimal value to a deterministic Java double literal.
   *
   * @param decimal decimal value from the resolved model
   * @return Java double literal string
   */
  static String decimalLiteral(BigDecimal decimal) {
    BigDecimal safeDecimal = decimal == null ? BigDecimal.ZERO : decimal;
    return safeDecimal.toPlainString() + "d";
  }

  /**
   * Builds a primitive count expression from one field expression.
   *
   * @param fieldExpression expression that resolves to the primitive count field
   * @param primitiveType primitive type of the count field
   * @return expression converted to a long-friendly count value
   */
  static String countExpression(String fieldExpression, PrimitiveType primitiveType) {
    return switch (primitiveType) {
      case UINT8 -> "(" + fieldExpression + " & 0xFFL)";
      case UINT16 -> "(" + fieldExpression + " & 0xFFFFL)";
      case UINT32 -> "(" + fieldExpression + " & 0xFFFFFFFFL)";
      case UINT64 -> fieldExpression;
      case INT8, INT16, INT32, INT64 -> fieldExpression;
    };
  }

  /**
   * Builds a local loop-item variable name.
   *
   * @param fieldName field/member name
   * @return deterministic local variable name
   */
  static String toLoopItemName(String fieldName) {
    return "item" + toPascalCase(fieldName);
  }

  /**
   * Builds a local loop-index variable name.
   *
   * @param fieldName field/member name
   * @return deterministic index variable name
   */
  static String toLoopIndexName(String fieldName) {
    return "index" + toPascalCase(fieldName);
  }

  /**
   * Converts a value to PascalCase for generated local variable names.
   *
   * @param value raw identifier text
   * @return PascalCase identifier fragment
   */
  static String toPascalCase(String value) {
    String[] parts = value.split("[^A-Za-z0-9]+");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      builder.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        builder.append(part.substring(1));
      }
    }
    return builder.length() == 0 ? value : builder.toString();
  }

  /**
   * Returns Java wrapper type for one primitive type.
   *
   * @param primitiveType primitive type to inspect
   * @return wrapper type name used in temporary lists
   */
  static String wrapperTypeForPrimitive(PrimitiveType primitiveType) {
    return switch (primitiveType) {
      case UINT8, INT16 -> "Short";
      case UINT16, INT32 -> "Integer";
      case UINT32, UINT64, INT64 -> "Long";
      case INT8 -> "Byte";
    };
  }

  /**
   * Builds a primitive-typed terminator literal expression.
   *
   * @param primitiveType primitive type of the terminator element
   * @param numericLiteral parsed numeric literal value
   * @return Java expression that yields a value in the primitive type
   */
  static String primitiveLiteralExpression(PrimitiveType primitiveType, BigInteger numericLiteral) {
    return switch (primitiveType) {
      case UINT8 -> "(short) " + numericLiteral;
      case UINT16 -> "(int) " + numericLiteral;
      case UINT32, UINT64 -> numericLiteral.longValue() + "L";
      case INT8 -> "(byte) " + numericLiteral;
      case INT16 -> "(short) " + numericLiteral;
      case INT32 -> "(int) " + numericLiteral;
      case INT64 -> numericLiteral.longValue() + "L";
    };
  }

  /**
   * Builds a terminator equality expression for one primitive local variable.
   *
   * @param valueExpression local variable expression
   * @param primitiveType primitive type of that variable
   * @param numericLiteral parsed terminator literal value
   * @return Java expression that evaluates to true when terminator is reached
   */
  static String terminatorComparisonExpression(
      String valueExpression, PrimitiveType primitiveType, BigInteger numericLiteral) {
    return switch (primitiveType) {
      case UINT8 -> "((" + valueExpression + " & 0xFFL) == " + numericLiteral + "L)";
      case UINT16 -> "((" + valueExpression + " & 0xFFFFL) == " + numericLiteral + "L)";
      case UINT32 -> "((" + valueExpression + " & 0xFFFFFFFFL) == " + numericLiteral + "L)";
      case UINT64 -> "(Long.compareUnsigned("
          + valueExpression
          + ", "
          + numericLiteral.longValue()
          + "L) == 0)";
      case INT8, INT16, INT32, INT64 -> "(((long) "
          + valueExpression
          + ") == "
          + numericLiteral
          + "L)";
    };
  }

  /**
   * Resolves and renders one if-condition expression for generated Java code.
   *
   * @param condition resolved condition tree
   * @param ownerPrefix expression prefix for the owning value ({@code this.} or {@code value.})
   * @return Java boolean expression that evaluates the condition
   */
  static String ifConditionExpression(ResolvedIfCondition condition, String ownerPrefix) {
    if (condition instanceof ResolvedIfComparison comparison) {
      return comparisonExpression(comparison, ownerPrefix);
    }
    if (condition instanceof ResolvedIfLogicalCondition logicalCondition) {
      return "("
          + ifConditionExpression(logicalCondition.left(), ownerPrefix)
          + " "
          + logicalJavaOperator(logicalCondition.operator())
          + " "
          + ifConditionExpression(logicalCondition.right(), ownerPrefix)
          + ")";
    }
    throw new IllegalStateException("Unsupported if condition node: " + condition);
  }

  /**
   * Converts one logical operator enum to its Java short-circuit symbol.
   *
   * @param operator logical operator in the resolved model
   * @return Java operator symbol used in generated conditions
   */
  private static String logicalJavaOperator(IfLogicalOperator operator) {
    return switch (operator) {
      case AND, OR -> operator.javaSymbol();
      default -> throw new IllegalStateException("Unsupported logical operator: " + operator);
    };
  }

  /**
   * Renders one primitive comparison node into Java code.
   *
   * @param comparison resolved comparison node
   * @param ownerPrefix expression prefix for the owning value ({@code this.} or {@code value.})
   * @return Java boolean expression for the comparison
   */
  private static String comparisonExpression(ResolvedIfComparison comparison, String ownerPrefix) {
    String fieldExpression = ownerPrefix + comparison.fieldName();
    if (comparison.fieldType() == PrimitiveType.UINT64) {
      return "(Long.compareUnsigned("
          + fieldExpression
          + ", Long.parseUnsignedLong(\""
          + comparison.literal()
          + "\")) "
          + unsignedLongComparisonSuffix(comparison.operator())
          + ")";
    }
    return "("
        + countExpression(fieldExpression, comparison.fieldType())
        + " "
        + signedLongComparisonOperator(comparison.operator())
        + " "
        + comparison.literal()
        + "L)";
  }

  /**
   * Converts a parsed operator to the Java operator used for signed long comparisons.
   *
   * @param operator comparison operator
   * @return Java operator for signed long comparison
   */
  private static String signedLongComparisonOperator(IfComparisonOperator operator) {
    return switch (operator) {
      case EQ -> "==";
      case NE -> "!=";
      case LT -> "<";
      case LTE -> "<=";
      case GT -> ">";
      case GTE -> ">=";
      default -> throw new IllegalStateException("Unsupported operator: " + operator);
    };
  }

  /**
   * Converts a parsed operator to the suffix used with {@link Long#compareUnsigned(long, long)}.
   *
   * @param operator comparison operator
   * @return Java comparison suffix, for example {@code == 0} or {@code < 0}
   */
  private static String unsignedLongComparisonSuffix(IfComparisonOperator operator) {
    return switch (operator) {
      case EQ -> "== 0";
      case NE -> "!= 0";
      case LT -> "< 0";
      case LTE -> "<= 0";
      case GT -> "> 0";
      case GTE -> ">= 0";
      default -> throw new IllegalStateException("Unsupported operator: " + operator);
    };
  }

  /**
   * Builds a primitive decode expression using the configured endian.
   *
   * @param primitiveType primitive type being decoded
   * @param endian optional endian override
   * @return Java expression that decodes one primitive value from input
   */
  static String primitiveDecodeExpression(PrimitiveType primitiveType, Endian endian) {
    String order = byteOrderExpression(endian);
    return switch (primitiveType) {
      case UINT8 -> "readUInt8(input)";
      case UINT16 -> "readUInt16(input, " + order + ")";
      case UINT32 -> "readUInt32(input, " + order + ")";
      case UINT64 -> "readUInt64(input, " + order + ")";
      case INT8 -> "readInt8(input)";
      case INT16 -> "readInt16(input, " + order + ")";
      case INT32 -> "readInt32(input, " + order + ")";
      case INT64 -> "readInt64(input, " + order + ")";
    };
  }

  /**
   * Returns the primitive type used by a count-field length mode.
   *
   * @param lengthMode resolved length mode
   * @param primitiveFieldByName primitive field lookup map
   * @return primitive type for the referenced count field
   */
  static PrimitiveType primitiveTypeForCountField(
      ResolvedLengthMode lengthMode, Map<String, PrimitiveType> primitiveFieldByName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      throw new IllegalStateException("Count-field length mode expected.");
    }
    PrimitiveType primitiveType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    if (primitiveType == null) {
      throw new IllegalStateException(
          "Unknown count field reference: " + resolvedCountFieldLength.ref());
    }
    return primitiveType;
  }
}
