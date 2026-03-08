package io.github.sportne.bms.codegen.common;

import io.github.sportne.bms.model.resolved.PrimitiveType;
import java.math.BigInteger;

/** Shared numeric literal and primitive-range rules for generators. */
public final class PrimitiveNumericRules {
  private static final BigInteger UINT64_MAX = new BigInteger("18446744073709551615");

  /** Creates a utility-only helper class. */
  private PrimitiveNumericRules() {}

  /**
   * Parses one integer literal used in BMS conditions and terminator values.
   *
   * <p>Supported formats:
   *
   * <ul>
   *   <li>decimal: {@code 123}
   *   <li>hex with prefix: {@code 0x7B}, {@code -0x7B}
   *   <li>plain hex fallback: {@code 7B}
   * </ul>
   *
   * @param literal raw literal text
   * @return parsed integer value
   * @throws NumberFormatException when the literal is not a valid integer
   */
  public static BigInteger parseNumericLiteral(String literal) {
    String trimmed = literal.trim();
    if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      return new BigInteger(trimmed.substring(2), 16);
    }
    if (trimmed.startsWith("-0x") || trimmed.startsWith("-0X")) {
      return new BigInteger(trimmed.substring(3), 16).negate();
    }
    if (trimmed.matches("-?[0-9]+")) {
      return new BigInteger(trimmed, 10);
    }
    return new BigInteger(trimmed, 16);
  }

  /**
   * Returns whether one value fits the range of a primitive integer type.
   *
   * @param value parsed integer value
   * @param primitiveType primitive target type
   * @return {@code true} when representable
   */
  public static boolean fitsPrimitiveRange(BigInteger value, PrimitiveType primitiveType) {
    return switch (primitiveType) {
      case UINT8 -> inRange(value, BigInteger.ZERO, BigInteger.valueOf(255));
      case UINT16 -> inRange(value, BigInteger.ZERO, BigInteger.valueOf(65_535));
      case UINT32 -> inRange(value, BigInteger.ZERO, BigInteger.valueOf(4_294_967_295L));
      case UINT64 -> inRange(value, BigInteger.ZERO, UINT64_MAX);
      case INT8 -> inRange(value, BigInteger.valueOf(-128), BigInteger.valueOf(127));
      case INT16 -> inRange(value, BigInteger.valueOf(-32_768), BigInteger.valueOf(32_767));
      case INT32 -> inRange(
          value, BigInteger.valueOf(Integer.MIN_VALUE), BigInteger.valueOf(Integer.MAX_VALUE));
      case INT64 -> inRange(
          value, BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE));
    };
  }

  /**
   * Returns whether one primitive type is unsigned.
   *
   * @param primitiveType primitive type to inspect
   * @return {@code true} for unsigned primitive types
   */
  public static boolean isUnsignedPrimitive(PrimitiveType primitiveType) {
    return primitiveType == PrimitiveType.UINT8
        || primitiveType == PrimitiveType.UINT16
        || primitiveType == PrimitiveType.UINT32
        || primitiveType == PrimitiveType.UINT64;
  }

  /**
   * Returns whether one value is between two inclusive bounds.
   *
   * @param value value to inspect
   * @param lowerInclusive lower bound (inclusive)
   * @param upperInclusive upper bound (inclusive)
   * @return {@code true} when in range
   */
  private static boolean inRange(
      BigInteger value, BigInteger lowerInclusive, BigInteger upperInclusive) {
    return value.compareTo(lowerInclusive) >= 0 && value.compareTo(upperInclusive) <= 0;
  }
}
