package io.github.sportne.bms.codegen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorMatch;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link SupportValidationRules}. */
final class SupportValidationRulesTest {
  @Test
  void isSupportedChecksumAlgorithmMatchesBackendContracts() {
    assertTrue(SupportValidationRules.isSupportedChecksumAlgorithm("crc16"));
    assertTrue(SupportValidationRules.isSupportedChecksumAlgorithm("crc32"));
    assertTrue(SupportValidationRules.isSupportedChecksumAlgorithm("crc64"));
    assertTrue(SupportValidationRules.isSupportedChecksumAlgorithm("sha256"));
    assertFalse(SupportValidationRules.isSupportedChecksumAlgorithm("md5"));
  }

  @Test
  void hasValidChecksumRangeUsesSharedRangeParser() {
    assertTrue(SupportValidationRules.hasValidChecksumRange("1..9"));
    assertFalse(SupportValidationRules.hasValidChecksumRange("9..1"));
  }

  @Test
  void validateUInt8LiteralReturnsExpectedStatuses() {
    assertEquals(
        SupportValidationRules.LiteralValidationStatus.VALID,
        SupportValidationRules.validateUint8Literal("255").status());
    assertEquals(
        SupportValidationRules.LiteralValidationStatus.OUT_OF_RANGE,
        SupportValidationRules.validateUint8Literal("999").status());
    assertEquals(
        SupportValidationRules.LiteralValidationStatus.INVALID,
        SupportValidationRules.validateUint8Literal("g1").status());
  }

  @Test
  void validateLengthModeHandlesCountFieldModes() {
    SupportValidationRules.LengthModeValidation validCount =
        validateLengthMode(
            new ResolvedCountFieldLength("count"),
            new PrimitiveTypeRef(PrimitiveType.UINT8),
            Map.of("count", PrimitiveType.UINT16));
    SupportValidationRules.LengthModeValidation missingCount =
        validateLengthMode(
            new ResolvedCountFieldLength("missing"),
            new PrimitiveTypeRef(PrimitiveType.UINT8),
            Map.of());

    assertEquals(SupportValidationRules.LengthModeValidationStatus.VALID, validCount.status());
    assertEquals(
        SupportValidationRules.LengthModeValidationStatus.COUNT_FIELD_MISSING,
        missingCount.status());
    assertEquals("missing", missingCount.detail());
  }

  @Test
  void validateLengthModeHandlesTerminatorFailuresAndSuccess() {
    SupportValidationRules.LengthModeValidation primitiveRequired =
        validateLengthMode(
            new ResolvedTerminatorValueLength("1"), new MessageTypeRef("Nested"), Map.of());
    SupportValidationRules.LengthModeValidation invalidLiteral =
        validateLengthMode(
            new ResolvedTerminatorValueLength("g1"),
            new PrimitiveTypeRef(PrimitiveType.UINT8),
            Map.of());
    SupportValidationRules.LengthModeValidation outOfRangeLiteral =
        validateLengthMode(
            new ResolvedTerminatorValueLength("1000"),
            new PrimitiveTypeRef(PrimitiveType.UINT8),
            Map.of());
    SupportValidationRules.LengthModeValidation missingTerminatorMatch =
        validateLengthMode(
            new ResolvedTerminatorField("node", null),
            new PrimitiveTypeRef(PrimitiveType.UINT8),
            Map.of());
    SupportValidationRules.LengthModeValidation validTerminatorPath =
        validateLengthMode(
            new ResolvedTerminatorField("node", new ResolvedTerminatorMatch("7")),
            new PrimitiveTypeRef(PrimitiveType.UINT8),
            Map.of());

    assertEquals(
        SupportValidationRules.LengthModeValidationStatus.TERMINATOR_REQUIRES_PRIMITIVE,
        primitiveRequired.status());
    assertEquals(
        SupportValidationRules.LengthModeValidationStatus.TERMINATOR_LITERAL_INVALID,
        invalidLiteral.status());
    assertEquals(
        SupportValidationRules.LengthModeValidationStatus.TERMINATOR_LITERAL_OUT_OF_RANGE,
        outOfRangeLiteral.status());
    assertEquals(
        SupportValidationRules.LengthModeValidationStatus.TERMINATOR_PATH_MISSING_MATCH,
        missingTerminatorMatch.status());
    assertEquals(
        SupportValidationRules.LengthModeValidationStatus.VALID, validTerminatorPath.status());
  }

  /**
   * Validates one length mode through the shared support rules.
   *
   * @param lengthMode resolved length mode under test
   * @param elementTypeRef resolved collection element type
   * @param primitiveFieldByName primitive field lookup map in scope
   * @return validation result from {@link SupportValidationRules}
   */
  private static SupportValidationRules.LengthModeValidation validateLengthMode(
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      Map<String, PrimitiveType> primitiveFieldByName) {
    return SupportValidationRules.validateLengthMode(
        lengthMode, elementTypeRef, primitiveFieldByName);
  }
}
