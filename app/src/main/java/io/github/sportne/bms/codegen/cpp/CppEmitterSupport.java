package io.github.sportne.bms.codegen.cpp;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.codegen.common.LengthModeRules;
import io.github.sportne.bms.codegen.common.MemberTraversal;
import io.github.sportne.bms.codegen.common.PrimitiveFieldIndex;
import io.github.sportne.bms.codegen.common.PrimitiveNumericRules;
import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.IfLogicalOperator;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedIfComparison;
import io.github.sportne.bms.model.resolved.ResolvedIfCondition;
import io.github.sportne.bms.model.resolved.ResolvedIfLogicalCondition;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Shared helper methods used by C++ encode/decode emitters.
 *
 * <p>These methods centralize formatting and validation behavior so both emitters stay aligned.
 */
final class CppEmitterSupport {
  /** Prevents instantiation of this static utility class. */
  private CppEmitterSupport() {}

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
   * Resolves one C++ message type name for a message-type reference.
   *
   * @param messageTypeName name of the referenced message type
   * @param currentNamespace namespace of the generated owner message
   * @param generationContext reusable lookup maps
   * @return C++ type name, either short or namespace-qualified
   */
  static String cppMessageTypeName(
      String messageTypeName,
      String currentNamespace,
      CppCodeGenerator.GenerationContext generationContext) {
    return CppTypeRenderer.cppMessageTypeName(messageTypeName, currentNamespace, generationContext);
  }

  /**
   * Returns whether one member list contains a checksum member recursively.
   *
   * @param members members to inspect
   * @return {@code true} when one checksum member is present
   */
  static boolean containsChecksumMember(List<ResolvedMessageMember> members) {
    return MemberTraversal.anyMatch(members, member -> member instanceof ResolvedChecksum);
  }

  /**
   * Maps one bit-field storage width to its primitive integer type.
   *
   * @param bitFieldSize storage size enum from the resolved model
   * @return primitive type used to read/write the bit-field storage word
   */
  static PrimitiveType bitFieldStoragePrimitive(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> PrimitiveType.UINT8;
      case U16 -> PrimitiveType.UINT16;
      case U32 -> PrimitiveType.UINT32;
      case U64 -> PrimitiveType.UINT64;
    };
  }

  /**
   * Renders one decimal value as a deterministic C++ floating-point literal.
   *
   * @param value decimal value from the resolved model
   * @return literal text with fixed decimal rendering
   */
  static String decimalLiteral(BigDecimal value) {
    return value == null ? "1.0" : value.toPlainString();
  }

  /**
   * Resolves one terminator literal for a length mode.
   *
   * @param lengthMode resolved length mode
   * @return terminator literal text, or {@code null} when not a terminator mode
   */
  static String terminatorLiteral(ResolvedLengthMode lengthMode) {
    if (lengthMode instanceof ResolvedTerminatorValueLength
        || lengthMode instanceof ResolvedTerminatorField) {
      return LengthModeRules.terminatorLiteral(lengthMode);
    }
    return null;
  }

  /**
   * Parses a numeric literal from XML using shared primitive rules.
   *
   * @param literal literal text from a terminator or comparison
   * @return parsed integer value
   */
  static BigInteger parseNumericLiteral(String literal) {
    return PrimitiveNumericRules.parseNumericLiteral(literal);
  }

  /**
   * Parses one checksum range and fails hard if it is unexpectedly invalid.
   *
   * @param rangeText checksum range text from the resolved model
   * @return parsed checksum range
   */
  static ChecksumRangeRules.ChecksumRange requiredChecksumRange(String rangeText) {
    return ChecksumRangeRules.require(rangeText);
  }

  /**
   * Renders one resolved if-condition tree as a C++ boolean expression.
   *
   * @param condition resolved condition tree
   * @param ownerPrefix expression prefix for member access (for example {@code this->})
   * @return C++ boolean expression
   */
  static String ifConditionExpression(ResolvedIfCondition condition, String ownerPrefix) {
    if (condition instanceof ResolvedIfComparison resolvedIfComparison) {
      return comparisonExpression(resolvedIfComparison, ownerPrefix);
    }
    if (condition instanceof ResolvedIfLogicalCondition resolvedIfLogicalCondition) {
      return "("
          + ifConditionExpression(resolvedIfLogicalCondition.left(), ownerPrefix)
          + " "
          + logicalCppOperator(resolvedIfLogicalCondition.operator())
          + " "
          + ifConditionExpression(resolvedIfLogicalCondition.right(), ownerPrefix)
          + ")";
    }
    throw new IllegalStateException("Unsupported if condition: " + condition);
  }

  /**
   * Renders one primitive comparison expression in C++ syntax.
   *
   * @param comparison resolved primitive comparison
   * @param ownerPrefix expression prefix for member access
   * @return C++ boolean comparison expression
   */
  private static String comparisonExpression(ResolvedIfComparison comparison, String ownerPrefix) {
    String fieldExpression = ownerPrefix + comparison.fieldName();
    String operator = comparisonOperatorSymbol(comparison.operator());
    if (isUnsignedPrimitive(comparison.fieldType())) {
      return "(static_cast<std::uint64_t>("
          + fieldExpression
          + ") "
          + operator
          + " "
          + comparison.literal()
          + "ULL)";
    }
    return "(static_cast<std::int64_t>("
        + fieldExpression
        + ") "
        + operator
        + " "
        + comparison.literal()
        + "LL)";
  }

  /**
   * Returns whether one primitive type should use unsigned comparison helpers.
   *
   * @param primitiveType primitive type to inspect
   * @return {@code true} when unsigned comparison semantics are required
   */
  private static boolean isUnsignedPrimitive(PrimitiveType primitiveType) {
    return PrimitiveNumericRules.isUnsignedPrimitive(primitiveType);
  }

  /**
   * Converts one resolved comparison operator to C++ source text.
   *
   * @param operator resolved comparison operator
   * @return C++ comparison symbol
   */
  private static String comparisonOperatorSymbol(IfComparisonOperator operator) {
    return switch (operator) {
      case EQ, NE, LT, LTE, GT, GTE -> operator.symbol();
      default -> throw new IllegalStateException("Unsupported comparison operator: " + operator);
    };
  }

  /**
   * Converts one logical operator to the matching C++ operator token.
   *
   * @param operator logical operator in the resolved model
   * @return C++ logical operator symbol
   */
  private static String logicalCppOperator(IfLogicalOperator operator) {
    return switch (operator) {
      case AND -> "&&";
      case OR -> "||";
      default -> throw new IllegalStateException("Unsupported logical operator: " + operator);
    };
  }

  /**
   * Renders one typed literal token for primitive terminator and comparison code.
   *
   * @param primitiveType primitive type that the literal targets
   * @param value parsed numeric literal
   * @return C++ literal token
   */
  static String primitiveLiteralExpression(PrimitiveType primitiveType, BigInteger value) {
    return "static_cast<"
        + primitiveType.cppTypeName()
        + ">("
        + numericLiteralToken(primitiveType, value)
        + ")";
  }

  /**
   * Renders one numeric literal token that matches a primitive type's signedness and width.
   *
   * @param primitiveType target primitive type
   * @param numericLiteral parsed numeric literal
   * @return C++ literal token
   */
  static String numericLiteralToken(PrimitiveType primitiveType, BigInteger numericLiteral) {
    if (primitiveType == PrimitiveType.UINT8
        || primitiveType == PrimitiveType.UINT16
        || primitiveType == PrimitiveType.UINT32
        || primitiveType == PrimitiveType.UINT64) {
      return numericLiteral.toString() + "ULL";
    }
    return numericLiteral.toString() + "LL";
  }

  /**
   * Builds one deterministic loop index variable name.
   *
   * @param ownerName owning field/member name
   * @return loop index variable name
   */
  static String toLoopIndexName(String ownerName) {
    return "index" + toPascalCase(ownerName);
  }

  /**
   * Builds one deterministic loop item variable name.
   *
   * @param ownerName owning field/member name
   * @return loop item variable name
   */
  static String toLoopItemName(String ownerName) {
    String pascal = toPascalCase(ownerName);
    if (pascal.isEmpty()) {
      return "item";
    }
    return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1) + "Item";
  }

  /**
   * Converts a name into PascalCase for generated identifiers.
   *
   * @param value raw field/member name
   * @return PascalCase text
   */
  static String toPascalCase(String value) {
    String[] parts = value.split("[^A-Za-z0-9]+");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      builder.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        builder.append(part.substring(1));
      }
    }
    return builder.toString();
  }
}
