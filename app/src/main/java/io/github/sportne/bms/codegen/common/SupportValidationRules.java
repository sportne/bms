package io.github.sportne.bms.codegen.common;

import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import java.math.BigInteger;
import java.util.Map;

/**
 * Shared backend-neutral validation rules used by Java and C++ support checkers.
 *
 * <p>These helpers expose status records instead of diagnostics so each backend can keep its own
 * diagnostic code and message text unchanged.
 */
public final class SupportValidationRules {
  /** Creates a utility-only helper class. */
  private SupportValidationRules() {}

  /**
   * Checks whether one checksum algorithm is implemented by current generators.
   *
   * @param algorithm checksum algorithm literal
   * @return {@code true} when the algorithm is implemented
   */
  public static boolean isSupportedChecksumAlgorithm(String algorithm) {
    return "crc16".equals(algorithm)
        || "crc32".equals(algorithm)
        || "crc64".equals(algorithm)
        || "sha256".equals(algorithm);
  }

  /**
   * Checks whether one checksum range string is syntactically valid.
   *
   * @param rangeText checksum range text
   * @return {@code true} when parsing succeeds
   */
  public static boolean hasValidChecksumRange(String rangeText) {
    return ChecksumRangeRules.parse(rangeText) != null;
  }

  /**
   * Validates one literal against the unsigned-byte range used by varString terminators.
   *
   * @param literal raw literal text
   * @return validation result
   */
  public static LiteralValidation validateUint8Literal(String literal) {
    return validatePrimitiveLiteral(literal, PrimitiveType.UINT8);
  }

  /**
   * Validates one literal against the range for a primitive type.
   *
   * @param literal raw literal text
   * @param primitiveType primitive type to validate against
   * @return validation result
   */
  public static LiteralValidation validatePrimitiveLiteral(
      String literal, PrimitiveType primitiveType) {
    try {
      BigInteger numericLiteral = PrimitiveNumericRules.parseNumericLiteral(literal);
      if (!PrimitiveNumericRules.fitsPrimitiveRange(numericLiteral, primitiveType)) {
        return new LiteralValidation(LiteralValidationStatus.OUT_OF_RANGE, literal);
      }
      return new LiteralValidation(LiteralValidationStatus.VALID, literal);
    } catch (NumberFormatException exception) {
      return new LiteralValidation(LiteralValidationStatus.INVALID, literal);
    }
  }

  /**
   * Validates vector/blob length-mode semantics in a backend-neutral way.
   *
   * @param lengthMode resolved length mode
   * @param elementTypeRef resolved collection element type
   * @param primitiveFieldByName primitive field lookup map in message scope
   * @return length-mode validation result
   */
  public static LengthModeValidation validateLengthMode(
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      Map<String, PrimitiveType> primitiveFieldByName) {
    if (lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      if (!primitiveFieldByName.containsKey(resolvedCountFieldLength.ref())) {
        return new LengthModeValidation(
            LengthModeValidationStatus.COUNT_FIELD_MISSING, resolvedCountFieldLength.ref());
      }
      return new LengthModeValidation(LengthModeValidationStatus.VALID, null);
    }

    String terminatorLiteral = resolveTerminatorLiteral(lengthMode);
    if (terminatorLiteral == null) {
      return new LengthModeValidation(
          LengthModeValidationStatus.TERMINATOR_PATH_MISSING_MATCH, null);
    }

    if (!(elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef)) {
      return new LengthModeValidation(
          LengthModeValidationStatus.TERMINATOR_REQUIRES_PRIMITIVE, null);
    }

    LiteralValidation literalValidation =
        validatePrimitiveLiteral(terminatorLiteral, primitiveTypeRef.primitiveType());
    if (literalValidation.status() == LiteralValidationStatus.INVALID) {
      return new LengthModeValidation(
          LengthModeValidationStatus.TERMINATOR_LITERAL_INVALID, terminatorLiteral);
    }
    if (literalValidation.status() == LiteralValidationStatus.OUT_OF_RANGE) {
      return new LengthModeValidation(
          LengthModeValidationStatus.TERMINATOR_LITERAL_OUT_OF_RANGE, terminatorLiteral);
    }
    return new LengthModeValidation(LengthModeValidationStatus.VALID, null);
  }

  /**
   * Resolves one terminator literal from resolved length-mode forms.
   *
   * @param lengthMode resolved length mode
   * @return resolved terminator literal, or {@code null} when no terminal match exists
   */
  private static String resolveTerminatorLiteral(ResolvedLengthMode lengthMode) {
    if (lengthMode instanceof ResolvedTerminatorValueLength resolvedTerminatorValueLength) {
      return resolvedTerminatorValueLength.value();
    }
    if (lengthMode instanceof ResolvedTerminatorField) {
      return LengthModeRules.terminatorLiteral(lengthMode);
    }
    return null;
  }

  /** Status values returned by {@link #validatePrimitiveLiteral(String, PrimitiveType)}. */
  public enum LiteralValidationStatus {
    VALID,
    INVALID,
    OUT_OF_RANGE
  }

  /**
   * Result record for primitive-literal validation.
   *
   * @param status literal validation status
   * @param literal literal text that was checked
   */
  public record LiteralValidation(LiteralValidationStatus status, String literal) {
    /**
     * Creates one literal-validation result.
     *
     * @param status literal validation status
     * @param literal literal text that was checked
     */
    public LiteralValidation(LiteralValidationStatus status, String literal) {
      this.status = java.util.Objects.requireNonNull(status, "status");
      this.literal = java.util.Objects.requireNonNull(literal, "literal");
    }
  }

  /**
   * Status values returned by {@link #validateLengthMode(ResolvedLengthMode, ResolvedTypeRef,
   * Map)}.
   */
  public enum LengthModeValidationStatus {
    VALID,
    COUNT_FIELD_MISSING,
    TERMINATOR_PATH_MISSING_MATCH,
    TERMINATOR_REQUIRES_PRIMITIVE,
    TERMINATOR_LITERAL_INVALID,
    TERMINATOR_LITERAL_OUT_OF_RANGE
  }

  /**
   * Result record for length-mode validation.
   *
   * @param status length-mode validation status
   * @param detail optional detail text (count-field ref or literal)
   */
  public record LengthModeValidation(LengthModeValidationStatus status, String detail) {
    /**
     * Creates one length-mode validation result.
     *
     * @param status length-mode validation status
     * @param detail optional detail text (count-field ref or literal)
     */
    public LengthModeValidation(LengthModeValidationStatus status, String detail) {
      this.status = java.util.Objects.requireNonNull(status, "status");
      this.detail = detail;
    }
  }
}
