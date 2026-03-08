package io.github.sportne.bms.codegen.common;

import java.math.BigInteger;

/**
 * Shared checksum-range parsing rules.
 *
 * <p>Ranges use {@code start..end} with zero-based inclusive bounds.
 */
public final class ChecksumRangeRules {
  private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

  /** Creates a utility-only helper class. */
  private ChecksumRangeRules() {}

  /**
   * Parses one checksum range string in the form {@code start..end}.
   *
   * @param rangeText checksum range text
   * @return parsed range, or {@code null} when invalid
   */
  public static ChecksumRange parse(String rangeText) {
    if (rangeText == null) {
      return null;
    }
    int separator = rangeText.indexOf("..");
    if (separator < 0 || separator != rangeText.lastIndexOf("..")) {
      return null;
    }

    String startText = rangeText.substring(0, separator).trim();
    String endText = rangeText.substring(separator + 2).trim();
    if (startText.isEmpty() || endText.isEmpty()) {
      return null;
    }

    try {
      BigInteger start = PrimitiveNumericRules.parseNumericLiteral(startText);
      BigInteger end = PrimitiveNumericRules.parseNumericLiteral(endText);
      if (start.signum() < 0 || end.signum() < 0 || start.compareTo(end) > 0) {
        return null;
      }
      if (start.compareTo(INT_MAX) > 0 || end.compareTo(INT_MAX) > 0) {
        return null;
      }
      return new ChecksumRange(start.intValueExact(), end.intValueExact());
    } catch (NumberFormatException | ArithmeticException exception) {
      return null;
    }
  }

  /**
   * Parses one checksum range and throws when it is unexpectedly invalid.
   *
   * @param rangeText checksum range text
   * @return parsed range
   * @throws IllegalStateException when the range text is invalid
   */
  public static ChecksumRange require(String rangeText) {
    ChecksumRange checksumRange = parse(rangeText);
    if (checksumRange == null) {
      throw new IllegalStateException("Unsupported checksum range: " + rangeText);
    }
    return checksumRange;
  }

  /**
   * Parsed checksum range bounds.
   *
   * @param startInclusive first byte index in range (inclusive)
   * @param endInclusive last byte index in range (inclusive)
   */
  public record ChecksumRange(int startInclusive, int endInclusive) {}
}
